#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The MSL twins of IDENTITY_SHADER and RED_SHADER in TeachingScreen.kt of the
// Android sample, for the first-shader lesson of the talk; the slides show both
// side by side. Kept apart from the ported production effect. SwiftUI hands the
// pixel color over as an argument, premultiplied; scaling channels by a
// constant keeps it consistent, so no unpremultiply step is needed here.

[[ stitchable ]] half4 identity(float2 position, half4 color) {
    return color;
}

[[ stitchable ]] half4 redTint(float2 position, half4 color) {
    half4 c = color;
    return half4(c.r, c.g * 0.15, c.b * 0.15, c.a);
}
