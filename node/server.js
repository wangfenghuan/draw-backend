// server.js
import { Server } from '@hocuspocus/server';
import { Logger } from '@hocuspocus/extension-logger';
// 如果没用到 Redis，可以先注释掉，或者保留 import
// import { Redis } from '@hocuspocus/extension-redis';
import dotenv from 'dotenv';
// 注意：你自定义的 api 工具类也需要支持 ESM 或以 .js 结尾
import api from './utils/api.js';

dotenv.config();

const server = new Server({
    port: process.env.PORT ? parseInt(process.env.PORT) : 1234,
    // 设置去抖动时间，避免频繁保存 (例如: 5秒内无变化才保存)
    debounce: 5000,
    // timeout: 30000, 

    extensions: [
        new Logger(),
    ],

    async onConnect(data) {
        const { request, documentName } = data;
        const roomId = documentName;
        let token = '';

        // 尝试从 URL 参数中获取 token (例如 ws://localhost:1234/room?token=xyz)
        try {
            const urlParts = request.url.split('?');
            if (urlParts.length > 1) {
                const searchParams = new URLSearchParams(urlParts[1]);
                token = searchParams.get('token') || searchParams.get('sessionId');
            }
        } catch (e) {
            console.error('Error parsing URL params:', e);
        }

        console.log(`[${roomId}] Connection attempt...`);
        console.log(`[${roomId}] Token found:`, token ? 'YES' : 'NO');

        // 调用 Spring Boot 鉴权
        try {
            // 直接传递 token，不再依赖 Cookie
            const authResult = await api.checkAuth(token, roomId);

            if (!authResult) {
                console.log(`[${roomId}] Auth failed.`);
                throw new Error('Unauthorized');
            }

            console.log(`[${roomId}] Authorized: ${authResult.nickname}`);

            return {
                user: {
                    id: authResult.userId,
                    name: authResult.nickname,
                },
                permission: authResult.permission
            };
        } catch (e) {
            console.error(`[${roomId}] Auth Error:`, e.message);
            throw e;
        }
    },

    async onStoreDocument(data) {
        const { document, documentName } = data;
        console.log(`[${documentName}] Storing document...`);

        // 尝试获取 xml 字段
        const xmlText = document.getText('xml');
        const xmlString = xmlText.toString();

        if (xmlString) {
            await api.saveSnapshot(documentName, xmlString, 0);
        } else {
            console.log(`[${documentName}] Document is empty, skipping save.`);
        }
    }
});

// 启动服务器
server.listen().then(({ url }) => {
    console.log(`🚀 Hocuspocus listening on ${url}`);
});