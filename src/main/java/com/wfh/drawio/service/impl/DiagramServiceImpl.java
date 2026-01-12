package com.wfh.drawio.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.constant.CachePrefixConstant;
import com.wfh.drawio.constant.RedisPrefixConstant;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.diagram.DiagramAddRequest;
import com.wfh.drawio.model.dto.diagram.DiagramQueryRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.mapper.DiagramMapper;
import com.wfh.drawio.model.enums.FileUploadBizEnum;
import com.wfh.drawio.model.vo.DiagramVO;
import com.wfh.drawio.model.vo.UserVO;
import com.wfh.drawio.service.DiagramService;
import com.wfh.drawio.service.PngDownloadStrategy;
import com.wfh.drawio.service.SpaceService;
import com.wfh.drawio.service.SvgDownloadStrategy;
import com.wfh.drawio.service.StrategyContext;
import com.wfh.drawio.service.XmlDownloadStrategy;
import com.wfh.drawio.service.UserService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图表服务实现
 *
 */
@Service
@Slf4j
public class DiagramServiceImpl extends ServiceImpl<DiagramMapper, Diagram> implements DiagramService {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    @Lazy
    private SvgDownloadStrategy svgDownloadStrategy;

    @Resource
    @Lazy
    private PngDownloadStrategy pngDownloadStrategy;

    @Resource
    @Lazy
    private XmlDownloadStrategy xmlDownloadStrategy;

    /**
     * 图表分页缓存
     */
    private Cache<String, Page<DiagramVO>> diagramsPageCache;

    @PostConstruct
    public void init() {
        diagramsPageCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(RandomUtil.randomInt(10, 30), TimeUnit.MINUTES)
                .build();
    }

    @Override
    public boolean tryAcquireLock(String roomName){
        String lockKey = "lock:snapshot:" + roomName;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (isLocked) {
                log.info("房间 [{}] 抢锁成功，进入冷却期(5min)", roomName);
            } else {
            }
            return isLocked;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 下载文件
     * @param remoteUrl
     * @param fileName
     * @param response
     */
    @Override
    public void download(String remoteUrl, String fileName, HttpServletResponse response) {
        HttpURLConnection connection = null;
        InputStream remoteInputStream = null;

        try {
            // 1. 建立连接
            URL url = new URL(remoteUrl);
            connection = (HttpURLConnection) url.openConnection();
            // 关键：设置超时，避免远程服务挂死导致本地线程池耗尽
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(60000);
            // 2. 检查远程状态
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // 如果远程文件不存在或报错，直接返回错误给前端
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "远程文件无法获取，状态码: " + responseCode);
                return;
            }
            // 3. 获取远程文件元数据
            String contentType = connection.getContentType();
            long contentLength = connection.getContentLengthLong();
            String finalFileName = (fileName != null && !fileName.isEmpty()) ? fileName : extractFileNameFromUrl(remoteUrl);
            String encodedFileName = URLEncoder.encode(finalFileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.reset();
            response.setContentType(contentType != null ? contentType : "application/octet-stream");
            if (contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            // 设为附件下载模式
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            // 核心：流对接 (输入流 -> 输出流)
            remoteInputStream = connection.getInputStream();
            StreamUtils.copy(remoteInputStream, response.getOutputStream());
            response.getOutputStream().flush();

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载错误");
        } finally {
            // 8. 资源清理
            if (remoteInputStream != null) {
                try {
                    remoteInputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }



    /**
     * 从URL截取文件名
     * @param url
     * @return
     */
    private String extractFileNameFromUrl(String url) {
        try {
            // 简单的截取逻辑，实际场景可能需要处理 URL 参数 (?ver=1.0)
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.isEmpty() ? "unknown_file" : name;
        } catch (Exception e) {
            return "download_file";
        }
    }

    /**
     * 校验数据
     *
     * @param diagram
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validDiagram(Diagram diagram, boolean add) {
        ThrowUtils.throwIf(diagram == null, ErrorCode.PARAMS_ERROR);
        String name = diagram.getName();
        String diagramCode = diagram.getDiagramCode();
        Long userId = diagram.getUserId();
        // 创建数据时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(ObjectUtils.isEmpty(userId), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        if (StringUtils.isNotBlank(name) || StringUtils.isNotBlank(diagramCode)) {
            ThrowUtils.throwIf(name.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
    }

    /**
     * 获取查询条件
     *
     * @param diagramQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Diagram> getQueryWrapper(DiagramQueryRequest diagramQueryRequest) {
        QueryWrapper<Diagram> queryWrapper = new QueryWrapper<>();
        if (diagramQueryRequest == null) {
            return queryWrapper;
        }
        Long id = diagramQueryRequest.getId();
        String name = diagramQueryRequest.getTitle();
        String searchText = diagramQueryRequest.getSearchText();
        Long spaceId = diagramQueryRequest.getSpaceId();
        Long userId = diagramQueryRequest.getUserId();
        boolean nullSpaceId = diagramQueryRequest.isNullSpaceId();
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("name", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        // 精确查询
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        return queryWrapper;
    }

    /**
     * 获取图表封装
     *
     * @param diagram
     * @param request
     * @return
     */
    @Override
    public DiagramVO getDiagramVO(Diagram diagram, HttpServletRequest request) {
        // 对象转封装类
        DiagramVO diagramVO = DiagramVO.objToVo(diagram);
        // 设置创建用户信息
        if (diagram.getUserId() != null) {
            User user = userService.getById(diagram.getUserId());
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                diagramVO.setUserVO(userVO);
            }
        }
        return diagramVO;
    }

    /**
     * 分页获取图表封装
     *
     * @param diagramPage
     * @param request
     * @return
     */
    @Override
    public Page<DiagramVO> getDiagramVOPage(Page<Diagram> diagramPage, HttpServletRequest request) {
        List<Diagram> diagramList = diagramPage.getRecords();
        Page<DiagramVO> diagramVOPage = new Page<>(diagramPage.getCurrent(), diagramPage.getSize(), diagramPage.getTotal());
        if (CollUtil.isEmpty(diagramList)) {
            return diagramVOPage;
        }
        // 对象列表 => 封装对象列表
        List<DiagramVO> diagramVOList = diagramList.stream().map(diagram -> {
            DiagramVO diagramVO = DiagramVO.objToVo(diagram);
            // 设置创建用户信息
            if (diagram.getUserId() != null) {
                User user = userService.getById(diagram.getUserId());
                if (user != null) {
                    UserVO userVO = new UserVO();
                    BeanUtils.copyProperties(user, userVO);
                    diagramVO.setUserVO(userVO);
                }
            }
            return diagramVO;
        }).collect(Collectors.toList());
        diagramVOPage.setRecords(diagramVOList);
        diagramVOPage.setCurrent(diagramPage.getCurrent());
        diagramVOPage.setSize(diagramPage.getSize());
        return diagramVOPage;
    }

    /**
     * 上传图表文件并更新空间额度（带事务）
     * 在事务内进行额度校验，确保并发安全
     * picSize = svgSize + pngSize
     */
    @Override
    public void uploadDiagramWithQuota(Long diagramId, Long spaceId, String fileUrl, Long fileSize, String extension, User loginUser) {
        transactionTemplate.execute(status -> {
            // 获取图表信息
            Diagram diagram = this.getById(diagramId);
            ThrowUtils.throwIf(diagram == null, ErrorCode.NOT_FOUND_ERROR, "图表不存在");

            // 获取旧的文件大小
            Long oldSvgSize = diagram.getSvgSize() != null ? diagram.getSvgSize() : 0L;
            Long oldPngSize = diagram.getPngSize() != null ? diagram.getPngSize() : 0L;
            Long oldPicSize = oldSvgSize + oldPngSize;

            // 判断文件类型
            boolean isSvg = "svg".equalsIgnoreCase(extension);
            boolean isPng = "png".equalsIgnoreCase(extension);

            // 计算新的文件大小
            Long newSvgSize = oldSvgSize;
            Long newPngSize = oldPngSize;

            if (isSvg) {
                newSvgSize = fileSize;
            } else if (isPng) {
                newPngSize = fileSize;
            } else {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件类型");
            }

            Long newPicSize = newSvgSize + newPngSize;
            long sizeDelta = newPicSize - oldPicSize;

            // 如果有空间ID，需要校验额度并更新
            if (spaceId != null) {
                // 在事务内重新查询空间，获取最新数据（加锁）
                Space space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

                // 权限校验
                if (!loginUser.getId().equals(space.getUserId())) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
                }

                // 额度校验（在事务内进行，确保并发安全）
                if (space.getTotalCount() >= space.getMaxCount()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
                }
                if (space.getTotalSize() + sizeDelta > space.getMaxSize()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
                }

                // 更新图表信息
                Diagram updateDiagram = new Diagram();
                updateDiagram.setId(diagramId);
                updateDiagram.setSvgSize(newSvgSize);
                updateDiagram.setPngSize(newPngSize);
                updateDiagram.setPicSize(newPicSize);
                if (isSvg) {
                    updateDiagram.setSvgUrl(fileUrl);
                } else if (isPng) {
                    updateDiagram.setPictureUrl(fileUrl);
                }
                boolean result = this.updateById(updateDiagram);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

                // 更新空间额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize + " + sizeDelta)
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            } else {
                // 没有空间ID，只更新图表信息
                Diagram updateDiagram = new Diagram();
                updateDiagram.setId(diagramId);
                updateDiagram.setSvgSize(newSvgSize);
                updateDiagram.setPngSize(newPngSize);
                updateDiagram.setPicSize(newPicSize);
                if (isSvg) {
                    updateDiagram.setSvgUrl(fileUrl);
                } else if (isPng) {
                    updateDiagram.setPictureUrl(fileUrl);
                }
                boolean result = this.updateById(updateDiagram);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            }
            return true;
        });
    }

    /**
     * 删除图表并释放额度（带事务）
     */
    @Override
    public void deleteDiagramWithQuota(Long id) {
        // 获取图表信息（在事务外）
        Diagram oldDiagram = this.getById(id);
        ThrowUtils.throwIf(oldDiagram == null, ErrorCode.NOT_FOUND_ERROR);

        transactionTemplate.execute(status -> {
            // 删除图表
            boolean result = this.removeById(id);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

            // 释放额度
            Long spaceId = oldDiagram.getSpaceId();
            if (spaceId != null) {
                // 使用 svgSize + pngSize 作为总大小
                Long svgSize = oldDiagram.getSvgSize() != null ? oldDiagram.getSvgSize() : 0L;
                Long pngSize = oldDiagram.getPngSize() != null ? oldDiagram.getPngSize() : 0L;
                Long picSize = svgSize + pngSize;

                if (picSize > 0) {
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, spaceId)
                            .setSql("totalSize = totalSize - " + picSize)
                            .setSql("totalCount = totalCount - 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                } else {
                    // 如果picSize为0，只减少count
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, spaceId)
                            .setSql("totalCount = totalCount - 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }
            }
            return true;
        });
    }

    /**
     * 创建图表并更新空间额度（带事务）
     */
    @Override
    public Long addDiagramWithQuota(DiagramAddRequest diagramAddRequest, User loginUser) {
        Diagram diagram = new Diagram();
        Long spaceId = diagramAddRequest.getSpaceId();
        BeanUtils.copyProperties(diagramAddRequest, diagram);
        diagram.setUserId(loginUser.getId());

        // 数据校验
        this.validDiagram(diagram, true);

        return transactionTemplate.execute(status -> {
            // 如果有空间ID，需要校验额度并更新
            if (spaceId != null) {
                Space space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

                // 权限校验
                if (!space.getUserId().equals(loginUser.getId())) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
                }

                // 额度校验
                if (space.getTotalCount() >= space.getMaxCount()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
                }

                // 保存图表
                boolean save = this.save(diagram);
                ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);

                // 更新空间额度（只增加count，因为此时还没有上传文件）
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            } else {
                // 没有空间ID，直接保存
                boolean save = this.save(diagram);
                ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
            }
            return diagram.getId();
        });
    }

    /**
     * 校验图表文件
     *
     * @param multipartFile 上传的文件
     * @param fileUploadBizEnum 业务类型
     */
    @Override
    public void validDiagramFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp", "svg").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }

    /**
     * 分页获取所有公共空间的图表（带多级缓存）
     * 缓存策略: Caffeine(L1) -> Redis(L2) -> DB
     */
    @Override
    public Page<DiagramVO> getPublicDiagramsByPage(DiagramQueryRequest pageRequest) {
        int current = pageRequest.getCurrent();
        int pageSize = pageRequest.getPageSize();
        // 构造key
        String redisKey = String.format(RedisPrefixConstant.ALL_DIAGRAM + "%s:%s:", current, pageSize);
        String cacheKey = String.format(CachePrefixConstant.ALL_DIAGRAM + "%s:%s", current, pageSize);
        // 先查询Caffeine中是否存在
        Page<DiagramVO> cachePage = diagramsPageCache.getIfPresent(cacheKey);
        if (cachePage == null) {
            // 本地缓存为空，去查询redis
            // 先查询redis是否存在
            String pageStr = stringRedisTemplate.opsForValue().get(redisKey);
            if (StringUtils.isEmpty(pageStr)) {
                // 如果Redis中是空的话，就查询数据库并构造缓存
                Page<Diagram> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
                LambdaQueryWrapper<Diagram> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.isNull(Diagram::getSpaceId);
                Page<Diagram> resultPage = this.page(page, queryWrapper);
                List<DiagramVO> diagramVOList = resultPage.getRecords().stream()
                        .map(DiagramVO::objToVo)
                        .toList();
                Page<DiagramVO> resPage = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
                resPage.setRecords(diagramVOList);
                resPage.setTotal(resultPage.getTotal());
                String jsonStr = JSONUtil.toJsonStr(resPage);
                // 设置Redis缓存
                stringRedisTemplate.opsForValue().set(redisKey, jsonStr, RandomUtil.randomInt(10, 40), TimeUnit.MINUTES);
                // 设置Caffeine缓存
                diagramsPageCache.put(cacheKey, resPage);
                return resPage;
            } else {
                // redis中不为空的话，直接反序列化之后返回给前端
                Page<DiagramVO> page = JSONUtil.toBean(pageStr, Page.class);
                // 同时设置本地缓存
                diagramsPageCache.put(cacheKey, page);
                return page;
            }
        }
        // 本地缓存不为空，直接返回
        return cachePage;
    }

    /**
     * 根据图表ID和文件类型下载图表文件
     */
    @Override
    public void downloadDiagramFile(Long diagramId, String type, String fileName, HttpServletResponse response) {
        Diagram diagram = this.getById(diagramId);
        ThrowUtils.throwIf(diagram == null, ErrorCode.NOT_FOUND_ERROR, "图表不存在");

        StrategyContext strategyContext = new StrategyContext();
        switch (type) {
            case "SVG":
                strategyContext.setDownloadStrategy(svgDownloadStrategy);
                strategyContext.execDownload(diagramId, fileName, response);
                break;
            case "PNG":
                strategyContext.setDownloadStrategy(pngDownloadStrategy);
                strategyContext.execDownload(diagramId, fileName, response);
                break;
            case "XML":
                strategyContext.setDownloadStrategy(xmlDownloadStrategy);
                strategyContext.execDownload(diagramId, fileName, response);
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件类型: " + type);
        }
    }

}
