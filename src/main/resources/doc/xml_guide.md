# Draw.io XML Syntax & Best Practices

> **CRITICAL**: You only need to generate `mxCell` elements. Wrapper tags (`mxfile`, `mxGraphModel`, `root`) and root cells (id="0", id="1") are added automatically.

## Core Syntax Rules
1. **Never Nest**: All `mxCell` are siblings. Do not put an `mxCell` inside another (exception for grouped elements visually, but XML-wise they remain siblings with parent references).
2. **IDs & Parents**: IDs start from `"2"`. Every cell needs a unique `id` and a `parent` attribute. `parent="1"` is for top-level shapes. `parent="<container-id>"` is for elements inside a group or swimlane.
3. **Text Nodes**: Text cells **must** include `whiteSpace=wrap;html=1;` in style.
4. **Line Breaks**: Use `&lt;br&gt;` for newlines. Never use `\n`.
5. **XML Constraints**: **No XML comments** (``). They break downstream ID matching.

## Shape Geometry & Containers (CRITICAL Constraint)
To avoid overlapping nodes (occlusion), you MUST calculate realistic `x` and `y` coordinates for every shape BEFORE outputting it.
- Space standard shapes by at least **150px horizontally** and **100px vertically**.
- Typical shape dimensions: `width="120" height="60"`.
- **Containers/VPCs**: Use `swimlane;startSize=40;` for dimensions like `width="400" height="300"`.
- **Anti-Overlap Rule**: Any child shapes inside a container MUST be placed at least **50px below** the container's `y` coordinate to prevent covering the container's title text!

## Edge Routing & Ports (ZERO SPAGHETTI)
Never draw diagonal intersecting lines causing a chaotic web.
- Always use modern routing over simplistic lines: `edgeStyle=orthogonalEdgeStyle;rounded=1;endArrow=blockThin;endFill=1;`
- **Dynamic/Async Flows**: Add `dashed=1;dashPattern=1 1;flowAnimation=1;` for data streams.
- **Port Explicitly**: Always add `exitX, exitY, entryX, entryY` constraints!
  - `exitX=1;exitY=0.5;entryX=0;entryY=0.5;` is perfect for a strict left-to-right connection.
  - `exitX=0.5;exitY=1;entryX=0.5;entryY=0;` is perfect for a strict top-to-bottom connection.
- **AVOID Manual Waypoints**: DO NOT use `<Array as="points">` to route edges manually unless completely unavoidable. LLMs struggle with 2D coordinates. Instead, strictly align your shapes on the same `x` or `y` axis and rely on `orthogonalEdgeStyle` to auto-route perfectly.

## Beautiful Aesthetics & Colors
Use pastel and modern flat colors to make architecture diagrams pop!
- **Base Shape Options**: `rounded=1;shadow=0;glass=0;sketch=0;`
- **Blue (Business Services)**: `fillColor=#DAE8FC;strokeColor=#6c8ebf;`
- **Green (Databases / Storage)**: `fillColor=#D5E8D4;strokeColor=#82b366;`
- **Orange (MQ / Cache / Async)**: `fillColor=#FFE6CC;strokeColor=#d79b00;`
- **Purple (Gateway / Proxies)**: `fillColor=#E1D5E7;strokeColor=#9673a6;`
- **Groups / VPCs (Containers)**: `fillColor=#F5F5F5;strokeColor=#B3B3B3;dashed=1;`