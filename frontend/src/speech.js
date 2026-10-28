const VOICE_LANG = { da: 'da-DK', de: 'de-DE' };

export const canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window;

/** Reads text aloud with the browser's own voice for the language; nothing leaves the device from Echo. */
export function speak(text, languageCode, rate = 0.9) {
  if (!canSpeak || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = VOICE_LANG[languageCode] || languageCode;
  const voice = window.speechSynthesis.getVoices().find(item => item.lang?.toLowerCase().startsWith(utterance.lang.slice(0, 2)));
  if (voice) utterance.voice = voice;
  utterance.rate = rate;
  window.speechSynthesis.speak(utterance);
}
