# Molecular Dynamics Service (Java)

A fully local, in-process molecular dynamics (MD) simulation service. It
integrates a Lennard-Jones particle system under periodic boundary
conditions, samples trajectories and structural statistics, writes
crash-safe checkpoints, and exposes everything over a local HTTP API.
All computation and data stay on this machine; no external services are
used.

## Physical model

- **Pair potential:** truncated Lennard-Jones with the *force-shifted*
  form. For particle types `a,b` the combining rules are Lorentz-Berthelot:
  `sigma_ab = (sigma_a + sigma_b)/2`, `epsilon_ab = sqrt(eps_a * eps_b)`.
- **Force shift:** at the cutoff `rc` the unshifted force magnitude
  `f(rc)` is subtracted from the force, and the potential is shifted by
  the matching constant so that both force and energy are continuous and
  exactly zero at `rc`. This strongly reduces integrator energy drift.
- **Boundary conditions:** orthorhombic periodic box with the minimum
  image convention. A configuration is rejected unless
  `rc < min(Lx,Ly,Lz)/2`.
- **Integrators:**
  - `NVE` — velocity Verlet.
  - `NVT` — Langevin thermostat with the BAOAB operator splitting
    (half-kick, half-drift, Ornstein-Uhlenbeck collision, half-drift,
    half-kick). Friction and target temperature are configurable.
- **Neighbor search:** linked-cell list used when every box edge holds at
  least 4 cells (the safe threshold for the half-neighbor stencil);
  smaller boxes use a direct loop.
- **Randomness:** a self-contained SplitMix64 generator plus Box-Muller
  Gaussian sampling, all math via `StrictMath`. The stream depends only
  on the seed, so runs are bit-for-bit reproducible on every JVM/platform.

## Units

Reduced (Lennard-Jones) units are used throughout:

| Quantity | Unit |
| --- | --- |
| length | `sigma` |
| mass | `m` |
| energy | `epsilon` |
| temperature | `epsilon / kB` (`kB = 1`) |
| time | `sqrt(m sigma^2 / epsilon)` |

For multiple species, per-species `mass`, `sigma`, `epsilon` are given in
these same reduced units; cross interactions use the combining rules
above.

## Configuration reference

All fields of `SimulationConfig` (JSON keys match the field names):

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `jobId` | string | generated | Unique job id; reused as the work directory name. |
| `boxLength` | `[Lx,Ly,Lz]` | required | Periodic box edge lengths; all must be finite and positive. |
| `species` | list | required | Each: `name`, `mass>0`, `sigma>0`, `epsilon>0`. |
| `particles` | list | required | Each: `speciesIndex`, `position[3] in [0,L)`, `velocity[3]`. |
| `cutoff` | double | `2.5` | Pair cutoff; must be `< min(L)/2`. |
| `minSeparation` | double | `0.8` | Minimum allowed minimum-image pair distance at startup. |
| `seed` | long | `1` | Deterministic RNG seed. |
| `ensemble` | string | `NVE` | `NVE` or `NVT`. |
| `timeStep` | double | `0.005` | Integration step (reduced time). |
| `duration` | double | `1.0` | Total simulated time. Number of steps is `round(duration/timeStep)`. |
| `targetTemperature` | double | `1.0` | Used for NVT and for Maxwell-Boltzmann velocity initialization. |
| `thermostatFriction` | double | `1.0` | Langevin friction rate (`gamma`, NVT only). |
| `sampleInterval` | long | `10` | Sample time series/RDF every this many steps. |
| `checkpointInterval` | long | `100` | Commit a checkpoint every this many steps. |
| `timeoutMillis` | double | `0` (off) | Wall-clock limit; on exceedance the job terminates as `TIMEOUT`. |
| `maxEnergyPerParticle` | double | `1e6` | Guard; larger kinetic or |potential| energy per particle triggers `NUMERICAL_DIVERGENCE`. |

If every particle velocity is zero, Maxwell-Boltzmann velocities are
drawn from `targetTemperature` with the net center-of-mass drift removed
and rescaled to exactly match the target kinetic temperature. Explicitly
non-zero velocities are kept as supplied.

### Validation

Before a job starts, the configuration is checked and **all** problems
are returned together as readable messages: non-positive box/species
parameters, out-of-range species index, non-3-component vectors,
positions outside `[0,L)`, pairs closer than `minSeparation` (minimum
image), cutoff vs. box size, unknown ensemble, non-positive
step/duration, and negative thermostat friction. Invalid requests never
start a run and the API responds `400` with an `invalid_request` body.

## Built-in example systems

FCC crystals, single LJ species (`sigma=epsilon=mass=1`):

| Name | Particles | Box edge | Cutoff | Ensemble | Purpose |
| --- | --- | --- | --- | --- | --- |
| `fcc32-nve` | 32 (2x2x2) | 3.10 | 1.5 | NVE | energy conservation / acceptance |
| `fcc108-nvt` | 108 (3x3x3) | 5.10 | 2.5 | NVT | thermostat convergence / acceptance |
| `fcc256-bench` | 256 (4x4x4) | 6.80 | 2.5 | NVT | benchmark |
| `fcc500-bench` | 500 (5x5x5) | 8.50 | 2.5 | NVT | benchmark (direct loop) |

Seeds and parameters are fixed; their statistical reference values are
pinned by `ReferenceStatisticsTest` and must only change deliberately.

## HTTP API

Base path: `/api/jobs`.

- `GET /examples` — list built-in example names.
- `POST /` — submit. Body is either
  `{"config": { ...SimulationConfig... } }` or
  `{"exampleName":"fcc32-nve","jobId":"j1","seed":7,"duration":2.0,"timeoutMillis":5000}`.
  Returns the job view immediately (jobs run in background threads).
- `GET /` — list jobs.
- `GET /{jobId}` — job view including the embedded `result` once finished.
- `POST /{jobId}/cancel` — request cooperative cancellation.
- `GET /{jobId}/timeseries` — CSV: `step,time,temperature,potentialEnergy,kineticEnergy,totalEnergy`.
- `GET /{jobId}/rdf` — CSV: `binCenter,rdf`.
- `GET /{jobId}/msd` — CSV: `time,msd`.

`JobView` fields: `jobId`, `status` (`QUEUED|RUNNING|FINISHED|TERMINATED|FAILED`),
`terminationReason` (`COMPLETED|CANCELLED|TIMEOUT|NUMERICAL_DIVERGENCE|FAILED`),
`errorMessage`, `result`.

CSV numbers use ASCII `.` and 17-significant-digit `%g` formatting, so
the output is locale-independent and machine parseable.

## Checkpoints and recovery

Checkpoint files (`checkpoints/checkpoint-0.mdcp`, `checkpoint-1.mdcp`,
two rotating slots under each job's work directory) contain the magic
line `MDCP1`, the current step, the RNG state, committed time-series
samples, RDF histogram/frame count, MSD origin/lag table, and every
particle's wrapped position, unwrapped position and velocity. A trailing
`crc32=<hex>` line covers the whole body.

- **Atomic writes:** data is written to a temp file then moved into place
  (`ATOMIC_MOVE` with a plain move fallback); no partial `.mdcp` or
  leftover `.tmp` files remain.
- **Corruption handling:** files are validated (magic, parse, CRC32).
  The newest valid file wins; corrupted files are deleted, so reading
  automatically falls back to the older valid slot.
- **Bit-identical resume:** because the exact RNG state and all dynamic
  state are restored, a cancelled/stopped run continued from its
  checkpoint reproduces the uninterrupted trajectory point for point;
  the returned time series/RDF/MSD are the full-run values. This is
  asserted by `ResumeTest`.
- On completion (and on cancel/timeout) the last committed checkpoint
  stays valid. On `NUMERICAL_DIVERGENCE` the previous good checkpoint is
  deliberately not overwritten.

## Numerical properties

- **Determinism:** identical input + seed produce identical trajectories
  and statistics (`DeterminismTest`, delta 0.0 on every sample).
- **NVE energy bound (documented and tested):** for `fcc32-nve`
  (`dt=0.002`, 1000 steps) the *secular* energy drift between the second
  and final quarter of the run is below `5e-4` of total energy, and the
  maximum oscillation about the late-run mean is below `3e-3`
  (`EnergyConservationTest`). These bounds are enforced automatically.
  Note: a cold crystal shows bounded energy oscillations but no
  systematic drift.
- **NVT control:** for `fcc108-nvt` the mean temperature over the second
  half is within 8% of the target, relative RMS fluctuation below 20%,
  and every second-half sample lies within +/-50% of the target
  (`ThermostatTest`).
- **Divergence:** non-finite state or energy above
  `maxEnergyPerParticle * N` ends the run as `NUMERICAL_DIVERGENCE`
  without corrupting the last good checkpoint (`DivergenceTest`).

## Complexity and scale

The linked-cell list makes force evaluation scale as `O(N)` at constant
density. Measured force-call cost (one machine, JDK 17, after warm-up):

| N | per call | per particle |
| --- | --- | --- |
| 864 | ~4.0 ms | ~4.7 us |
| 2048 | ~10.3 ms | ~5.0 us |
| 4000 | ~21.2 ms | ~5.3 us |

Per-particle cost grows only ~7% from 864 to 2048 particles and ~6%
from 2048 to 4000, i.e. an effective exponent of about 1.1 (near
linear); `ScalingTest` enforces < 50% per-particle growth.

**Practical scale:** with `rc=2.5` the cell list engages once the box
holds at least 4 cells per edge, i.e. `L >= 4 rc = 10`. For the built-in
FCC lattices at `a=1.7` that corresponds to a `6x6x6` supercell =
864 particles; the 108/256/500 examples deliberately run the direct
loop, which is still fast up to a few hundred particles. The
implementation comfortably handles a few thousand particles (the tested
4000-particle case is ~21 ms/step). Beyond O(10^4) particles memory and
single-thread throughput become the limiting factors; parallel force
kernels would be the next scaling step and are intentionally out of
scope.

## Concurrency and failure isolation

Each job owns its own work directory, `SimulationEngine`, state arrays,
RNG and daemon thread; jobs share no mutable state. A validation failure
is rejected before a job is created, and a runtime exception in one job
is recorded as `FAILED` with a readable message without affecting other
jobs (`ConcurrencyTest`). No partial output is published: results appear
only after a terminal state, and all on-disk state goes through atomic
checkpoint writes.

## Build and test

```bash
mvn test        # full suite (~20s), incl. physics, checkpoints, web, scaling
mvn spring-boot:run
```

Quick start once the service is running:

```bash
curl -X POST localhost:8080/api/jobs -H 'Content-Type: application/json' \
  -d '{"exampleName":"fcc32-nve","jobId":"demo"}'
curl localhost:8080/api/jobs/demo
curl localhost:8080/api/jobs/demo/timeseries
```

Configuration property:

| Property | Default | Meaning |
| --- | --- | --- |
| `md.work-dir` | `target/md-work` | Root directory holding per-job checkpoint/output data. |
