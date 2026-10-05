#version 150

// Renders an item sprite as a pale, glowing, translucent "ghost": the texture's shape and shading
// survive (via luminance), but its colors are replaced by a cool spectral white.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }
    return smoothstep(fogEnd, fogStart, vertexDistance);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }
    float luminance = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    vec3 ghost = mix(vec3(0.78, 0.86, 1.0), vec3(1.0), luminance);
    float alpha = vertexColor.a * ColorModulator.a * (0.55 + 0.45 * luminance);
    fragColor = vec4(ghost * vertexColor.rgb * ColorModulator.rgb, alpha * linear_fog_fade(vertexDistance, FogStart, FogEnd));
}
