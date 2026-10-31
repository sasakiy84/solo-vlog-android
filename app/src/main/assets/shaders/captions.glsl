precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uDayMask;
uniform sampler2D uClipMask;
uniform sampler2D uTimeMask;
uniform sampler2D uPlaceMask;
uniform vec4 uDayRect;
uniform vec4 uClipRect;
uniform vec4 uTimeRect;
uniform vec4 uPlaceRect;
uniform vec4 uInkColors;
varying vec2 vTexSamplingCoord;

vec3 caption(vec3 color, sampler2D mask, vec4 rect, float ink) {
  if (rect.z <= 0.0 || rect.w <= 0.0) return color;
  vec2 local = (vTexSamplingCoord - rect.xy) / rect.zw;
  if (local.x < 0.0 || local.x > 1.0 || local.y < 0.0 || local.y > 1.0) return color;
  float alpha = texture2D(mask, vec2(local.x, 1.0 - local.y)).a;
  if (alpha < 0.001) return color;
  return mix(color, vec3(ink), alpha);
}
void main() {
  vec4 frame = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 color = caption(frame.rgb, uDayMask, uDayRect, uInkColors.x);
  color = caption(color, uClipMask, uClipRect, uInkColors.y);
  color = caption(color, uPlaceMask, uPlaceRect, uInkColors.z);
  color = caption(color, uTimeMask, uTimeRect, uInkColors.w);
  gl_FragColor = vec4(color, frame.a);
}
