// Quality presets. Chosen automatically from the GPU / device, overridable with ?q=low|medium|high
export const QUALITY = {
  low: { pixelRatio: 0.75, lodK: 1.6, shadows: false, shadowSize: 1024, bloom: true, clouds: true, cloudSteps: 20, structLod: 0.6 },
  medium: { pixelRatio: 1.0, lodK: 2.0, shadows: true, shadowSize: 2048, bloom: true, clouds: true, cloudSteps: 32, structLod: 0.85 },
  high: { pixelRatio: 1.5, lodK: 2.6, shadows: true, shadowSize: 3072, bloom: true, clouds: true, cloudSteps: 44, structLod: 1.15 },
};

export function pickQuality() {
  try {
    const c = document.createElement('canvas');
    const gl = c.getContext('webgl2');
    if (!gl) return 'low';
    const ext = gl.getExtension('WEBGL_debug_renderer_info');
    const r = (ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER)) || '';
    const mobile = /Android|iPhone|iPad|Mobile/i.test(navigator.userAgent);
    if (mobile || /SwiftShader|llvmpipe|Software/i.test(r)) return 'low';
    if (/RTX|Radeon RX|Apple M[1-9] (Pro|Max|Ultra)|GeForce GTX 1[0-9]{3}/i.test(r)) return 'high';
    return 'medium';
  } catch (e) {
    return 'medium';
  }
}
