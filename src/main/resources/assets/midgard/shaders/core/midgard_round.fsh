#version 330

// Eigener Midgard-Fragment-Shader: abgerundetes Rechteck per Signed-Distance-
// Function, antialiased über Screen-Derivate (fwidth) – komplett ohne
// Custom-Uniforms. RADIUS (in Pixeln) kommt als Shader-Define aus der Pipeline.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 uv = texCoord0;
    vec2 duv = fwidth(uv);
    vec4 col = vertexColor * ColorModulator * texture(Sampler0, uv);
    if (duv.x > 0.0 && duv.y > 0.0) {
        vec2 p = (uv - 0.5) / duv;       // Pixel-Offset vom Zentrum
        vec2 b = 0.5 / duv;              // halbe Größe in Pixeln
        float r = min(RADIUS, min(b.x, b.y));
        float d = sdRoundBox(p, b, r);
        float aa = fwidth(d) + 1e-4;
        col.a *= 1.0 - smoothstep(0.0, aa, d);
    }
    if (col.a <= 0.001) discard;
    fragColor = col;
}
