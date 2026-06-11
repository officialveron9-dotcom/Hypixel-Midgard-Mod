#version 330

// Midgard-Text-Shader: rendert SDF-Glyphen (Distanzfeld im Alpha-Kanal) mit
// weicher Kante via smoothstep – skalierungsunabhängig scharf.
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

void main() {
    float dist = texture(Sampler0, texCoord0).a;
    float aa = fwidth(dist);
    float a = smoothstep(0.5 - aa, 0.5 + aa, dist);
    vec4 col = vec4(vertexColor.rgb, vertexColor.a * a) * ColorModulator;
    if (col.a <= 0.001) {
        discard;
    }
    fragColor = col;
}
