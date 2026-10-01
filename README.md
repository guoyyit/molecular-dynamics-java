# 本地分子动力学仿真服务 (molecular-dynamics-java)

一个完全在本机运行的 Lennard-Jones 分子动力学 (MD) 仿真服务。输入粒子体系配置，
进程内完成数值积分与轨迹采样，输出温度/能量时间序列、径向分布函数 (RDF)、
均方位移 (MSD) 以及可恢复运行状态的原子检查点。不依赖任何外部计算服务。

## 功能总览

- 周期性边界条件 (PBC) 与最小镜像约定，截断平移 Lennard-Jones 对势。
- 两种系综：NVE（Velocity Verlet）与 NVT（BAOAB Langevin 恒温器）。
- 固定随机种子的逐位确定性；同输入同种子重复运行结果完全一致。
- 启动前严格参数校验（负质量、位置越界、过度重叠、截断越界、缺目标温度等）。
- 原子写入检查点，SHA-256 完整性校验，损坏时自动回退到更早的有效检查点。
- 检查点续跑与不中断运行逐点一致（含 RNG 状态、样本与分析累积量）。
- 受控终止：用户取消、墙钟超时、数值发散（NaN/温度上限/位移上限）。
- 元胞列表近线性扩展；盒子过小（最短边 < 3×截断）时回退 O(N²) 直接遍历。
- 固定参数的内置示例体系（FCC 氩流体，NVE / NVT）。
- 多任务并发，任务目录完全隔离，单任务失败/取消不影响其他任务。
- REST API + 稳定可解析输出（`result.json`、`samples.csv`、`rdf.csv`、`msd.csv`）。

## 快速开始

```bash
mvn spring-boot:run
```

服务默认监听 8080 端口。所有任务数据写入 `md.work-dir`（默认 `md-work/`）。

### 查看内置示例

```bash
curl http://localhost:8080/api/examples
curl http://localhost:8080/api/examples/argon-fcc-nve
```

### 提交并跟踪一个仿真任务

```bash
# 提交（body 为完整配置，可直接修改内置示例）
curl -X POST http://localhost:8080/api/simulations \
  -H 'Content-Type: application/json' \
  -d @<(curl -s http://localhost:8080/api/examples/argon-fcc-nvt)

# 查询状态（任务完成后含统计摘要）
curl http://localhost:8080/api/simulations/<job-id>

# 取消
curl -X POST http://localhost:8080/api/simulations/<job-id>/cancel

# 从最近有效检查点续跑（steps=0 表示使用原配置步数）
curl -X POST 'http://localhost:8080/api/simulations/<job-id>/resume?steps=2000'
```

### 运行测试

```bash
mvn test                                  # 全部 26 个测试（基准测试默认跳过）
mvn test -DrunBenchmarks=true            # 额外运行近线性扩展基准
```

## 输出格式

每个任务写入独立目录 `md-work/jobs/<job-id>/`：

- `input-config.json`：原子写入的原始输入配置。
- `checkpoints/checkpoint-<step>.json`：带版本与 SHA-256 的原子检查点，默认保留最近 3 个。
- `output/result.json`：完整结果（含终止原因、均值、能量漂移与全部样本）。
- `output/samples.csv`：`step,time,temperature,kinetic_energy,potential_energy,total_energy`。
- `output/rdf.csv`：`r,g(r)`，r 取箱中心。
- `output/msd.csv`：`lag_time,msd`。

所有文件均通过“临时文件 + 原子 rename”提交，不会留下半写入状态。

## 终止原因

`GET /api/simulations/<id>` 与 `result.json` 中的 `terminationReason` 取值：

- `COMPLETED`：跑满目标步数。
- `CANCELLED`：用户取消；最近一次已提交检查点保持有效。
- `TIMEOUT`：超过 `timeoutMillis` 墙钟时间。
- `DIVERGED`：出现 NaN/Infinity、温度超 `maxTemperature` 或未折叠位移超上限。
- `FAILED`：I/O 或其他内部错误。

## 文档

- 物理模型、数值方法、单位制、配置项、能量漂移与复杂度实测见
  [`docs/PHYSICS.md`](docs/PHYSICS.md) 与 [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)。
