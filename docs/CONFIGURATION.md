# 配置项说明

配置以 JSON 通过 `POST /api/simulations` 提交，或直接取用
`GET /api/examples/{name}` 返回的内置示例。字段名如下（全部为小写驼峰）。

## 体系描述

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `"simulation"` | 任务名称，仅用于展示 |
| `box` | number[3] | `[10,10,10]` | 盒子边长 `[Lx,Ly,Lz]`，必须均为正有限数 |
| `species` | array | `[]` | 粒子种类列表 `{name,mass}` |
| `species[].name` | string | — | 种类名，不可重复、非空 |
| `species[].mass` | number | — | 质量，必须为正有限数（负质量/零/NaN 拒绝） |
| `particles` | array | `[]` | 粒子列表 `{species,position,velocity}` |
| `particles[].species` | string | — | 必须引用已定义的 `species` 名称 |
| `particles[].position` | number[3] | — | 初始位置，必须落在半开区间 `[0,L_axis)` |
| `particles[].velocity` | number[3] | 可选 | 初始速度；缺省为零 |

位置越界（含超出容差 1e-9）会被拒绝。初始速度会自动扣除质心平动；
NVT 且给定非零初始速度时，会整体缩放到 `targetTemperature`。

## Lennard-Jones 势

| 字段 | 默认 | 约束 |
| --- | --- | --- |
| `epsilon` | `1.0` | 正有限数 |
| `sigma` | `1.0` | 正有限数 |
| `cutoff` | `2.5` | 正且 `< min(box)/2` |
| `minSeparation` | `0.87` | 正有限数；重叠下限（单位 σ） |

启动前对所有粒子对按最小镜像距离检查：若最小距离 < `minSeparation × sigma`
则拒绝配置并给出粒子下标与实际距离。

## 运行控制

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `ensemble` | `"NVE"` | `NVE` 或 `NVT` |
| `timeStep` | `0.005` | 约化时间单位，正有限数 |
| `steps` | `1000` | 总积分步数，≥1 |
| `targetTemperature` | `null` | NVT 必填的正数；NVE 忽略 |
| `thermostatFriction` | `1.0` | Langevin 摩擦 γ，非负 |
| `seed` | `1234` | 64 位随机种子，决定 NVT 噪声与示例初速度 |
| `timeoutMillis` | `0` | 墙钟超时（毫秒），0 表示不限时 |

## 采样与检查点

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `sampleInterval` | `10` | 每隔多少步记录一个样本（step 0 总是记录） |
| `checkpointInterval` | `500` | 每隔多少步原子提交一次检查点 |
| `retainedCheckpoints` | `3` | 保留的最近检查点数量，≥1 |
| `rdfBins` | `400` | RDF 直方图箱数，≥10 |

## 数值发散保护

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `maxTemperature` | `100.0` | 瞬时温度上限，超过即 `DIVERGED`；`null` 关闭 |
| `maxUnwrappedDistance` | `1e6` | 未折叠坐标绝对值上限 |

此外任何 NaN/Infinity（位置、速度、力、势能）都会立即触发 `DIVERGED`。

## 服务级配置 (`application.properties`)

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `md.work-dir` | `md-work` | 全部任务目录的根 |
| `md.max-concurrent` | `4` | 并发工作线程数（守护线程池） |

## 校验失败响应

非法配置返回 `400 Bad Request`，body 为
`{"error":"配置校验失败:\n - <可读原因1>\n - <可读原因2>"}`，
一次返回所有发现的问题，而不是只报第一个。
