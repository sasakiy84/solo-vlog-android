precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uDayMask;
uniform sampler2D uClipMask;
uniform sampler2D uTimeMask;
uniform vec4 uDayRect;
uniform vec4 uClipRect;
uniform vec4 uTimeRect;
uniform vec3 uLumaWeights;
uniform float uLumaThreshold;
varying vec2 vTexSamplingCoord;

vec3 caption(vec3 color, sampler2D mask, vec4 rect) {
  if (rect.z <= 0.0 || rect.w <= 0.0) return color;
  vec2 local = (vTexSamplingCoord - rect.xy) / rect.zw;
  if (local.x < 0.0 || local.x > 1.0 || local.y < 0.0 || local.y > 1.0) return color;
  float alpha = texture2D(mask, vec2(local.x, 1.0 - local.y)).a;
  if (alpha < 0.001) return color;
  // Pick one ink color for each label from the current, unmodified video frame.
  float luminance = 0.0;
  for (int y = 0; y < 3; y++) {
    for (int x = 0; x < 5; x++) {
      vec2 sampleAt = rect.xy + rect.zw * vec2((float(x) + 0.5) / 5.0, (float(y) + 0.5) / 3.0);
      luminance += dot(texture2D(uTexSampler, sampleAt).rgb, uLumaWeights);
    }
  }
  float ink = luminance / 15.0 > uLumaThreshold ? 0.0 : 1.0;
  return mix(color, vec3(ink), alpha);
}
void main() {
  vec4 frame = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 color = caption(frame.rgb, uDayMask, uDayRect);
  color = caption(color, uClipMask, uClipRect);
  color = caption(color, uTimeMask, uTimeRect);
  gl_FragColor = vec4(color, frame.a);
}
