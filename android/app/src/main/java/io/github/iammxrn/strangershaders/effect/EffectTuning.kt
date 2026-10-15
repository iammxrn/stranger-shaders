package io.github.iammxrn.strangershaders.effect

/**
 * Number of loop particles the route shaders (AGSL grade and GLSL video) render.
 *
 * 0 since 2026-09-26: the routes draw the iOS grid snow instead, once, as a full-screen overlay
 * (effect/snow). The loop is the most expensive knob of the original effect. Particles are not a
 * buffer: every pixel re-derives every particle, so cost is pixels times particles. Measured on a
 * Pixel 10 at 1080x2424, the whole effect cost 13 ms per frame with none and 121 ms with the
 * production value of 80; the sample shipped 16 until the snow replaced it.
 *
 * The teaching stages set their own count and still demonstrate the loop. Raising this again
 * brings the loop back on the routes, on top of the snow.
 */
const val ParticleCount = 0
