#include <metal_stdlib>
using namespace metal;

// Four dark ribbons growing in from the screen edges, ported from the
// production Upside Down overlay. The centerline of a vine depends only on
// the seed and the screen size, and `progress` merely truncates it, so the
// walk runs once per seed and every frame only decides how many points are
// visible. The ribbon itself is built in the vertex shader: the strip vertex
// pair for point `i` is the point offset along its normal by the tapered
// half width, exactly as the per frame mesh pass used to do.

struct VinesUniforms {
  float2 screenSize;
  float progress;
  float mainWidth;
  uint seed;
};

constant uint kVinesCount = 4;
constant uint kMaxPoints = 1024;
constant float kStep = 4.0;

static inline uint hashUint2(uint2 v) {
  v = v * 1664525u + 1013904223u;
  v.x ^= (v.y << 5) | (v.y >> 27);
  v.y ^= (v.x << 7) | (v.x >> 25);
  return v.x ^ v.y;
}

static inline float noise2(float2 pos, uint seed) {
  uint2 h = uint2((uint)pos.x, (uint)pos.y) ^ uint2(seed * 7411u, seed * 1931u);
  return float(hashUint2(h) & 0xFFFFFFu) / float(0xFFFFFFu);
}

static inline float vinesMaxLength(float2 screen) {
  return length(screen) * 0.53;
}

// MARK: - Centerlines, once per seed

kernel void vinesCenterline(constant VinesUniforms &uniforms [[buffer(0)]],
                            device float2 *lines [[buffer(1)]],
                            device uint *pointCounts [[buffer(2)]],
                            uint vine [[threadgroup_position_in_grid]]) {
  if (vine >= kVinesCount) return;

  float2 screen = uniforms.screenSize;

  float rand1 = noise2(float2(float(vine * 317 + 123), float(uniforms.seed * 73)), uniforms.seed * 11u + vine * 13u);
  float rand2 = noise2(float2(float(vine * 419 + 456), float(uniforms.seed * 97)), uniforms.seed * 17u + vine * 19u);
  rand1 = (rand1 + 1.0) * 0.5;
  rand2 = (rand2 + 1.0) * 0.5;

  // One vine per edge, the start point wanders along that edge with the seed.
  float2 start, dir;
  if (vine == 0) {
    start = float2(0, screen.y * (0.1 + rand1 * 0.8));
    dir = float2(1, 0);
  } else if (vine == 1) {
    start = float2(screen.x, screen.y * (0.1 + rand2 * 0.8));
    dir = float2(-1, 0);
  } else if (vine == 2) {
    start = float2(screen.x * (0.1 + rand1 * 0.8), 0);
    dir = float2(0, 1);
  } else {
    start = float2(screen.x * (0.1 + rand2 * 0.8), screen.y);
    dir = float2(0, -1);
  }

  device float2 *line = lines + vine * kMaxPoints;
  line[0] = start;
  uint count = 1;

  float maxLen = vinesMaxLength(screen);
  float traveled = 0;

  for (uint i = 1; i < kMaxPoints; i++) {
    float2 prev = line[i - 1];

    // The first segments stay straight so the vine leaves the edge cleanly.
    if (i > 3) {
      float n = noise2(prev * 0.006, uniforms.seed + vine * 13u);
      float jitter = (n * 12.0 - 6.0) * (3.14159 / 180.0);
      float c = cos(jitter);
      float s = sin(jitter);
      dir = normalize(float2(dir.x * c - dir.y * s, dir.x * s + dir.y * c));
    }

    float2 next = prev + dir * kStep;
    if (next.x < 0 || next.x > screen.x || next.y < 0 || next.y > screen.y) break;

    line[i] = next;
    count = i + 1;
    traveled += kStep;
    if (traveled >= maxLen) break;
  }

  pointCounts[vine] = count;
}

// MARK: - Ribbons, every frame

vertex float4 vinesVertex(uint vid [[vertex_id]],
                          constant VinesUniforms &uniforms [[buffer(0)]],
                          const device float2 *lines [[buffer(1)]],
                          const device uint *pointCounts [[buffer(2)]]) {
  uint vine = vid / (kMaxPoints * 2);
  uint local = vid % (kMaxPoints * 2);
  uint index = local / 2;
  float side = (local % 2 == 0) ? 1.0 : -1.0;

  const device float2 *line = lines + vine * kMaxPoints;

  // The number of points the truncated walk would have produced: the start
  // point plus every step that began before targetLen. Dividing by the step
  // is exact in float, so the count matches the walk's own comparison.
  float targetLen = vinesMaxLength(uniforms.screenSize) * uniforms.progress;
  float steps = targetLen / kStep;
  uint grown = steps > 0.0 ? uint(ceil(steps)) : 0u;
  uint validPoints = min(pointCounts[vine], 1u + grown);

  // The unused tail of the strip collapses onto the last visible point and
  // draws zero-area triangles.
  uint i = min(index, validPoints - 1);
  float2 p = line[i];

  // Normal from the neighbouring points: forward at the root, backward at
  // the tip, averaged in between.
  float2 d;
  if (i == 0 && validPoints > 1) {
    d = line[i + 1] - p;
  } else if (i == validPoints - 1 && i > 0) {
    d = p - line[i - 1];
  } else if (i > 0 && i < validPoints - 1) {
    d = line[i + 1] - line[i - 1];
  } else {
    d = float2(1, 0);
  }
  float2 nrm = length(d) > 0.001 ? float2(-d.y, d.x) / length(d) : float2(0, 1);

  float t = float(i) / float(max(1u, validPoints - 1));
  float halfWidth = mix(uniforms.mainWidth, 6.0, t) * 0.5;
  float2 v = p + nrm * halfWidth * side;

  float2 ndc = float2((v.x / uniforms.screenSize.x) * 2.0 - 1.0,
                      (1.0 - v.y / uniforms.screenSize.y) * 2.0 - 1.0);
  return float4(ndc, 0, 1);
}

fragment float4 vinesFragment() {
  return float4(0.0, 0.0, 0.0, 0.92);
}
