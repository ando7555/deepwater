/*
 * The Echo home selection, written once. echoHome, startEcho and chooseEchoLanguage all return
 * the same EchoHome shape, so they share these fields instead of three hand-copied lists.
 */
const EXERCISE_FIELDS = 'id position taskType prompt lastCorrect options { key text }';
const AUDIO_FIELDS = 'mediaType sourceTitle creator sourceUrl playbackUrl editorialBand topicTags speechRate vocabularyDensity idiomDensity '
  + 'pronunciationLoad topicFamiliarity beforeListeningPrompt gistPrompt focusPrompt transcriptAllowed transcriptText rightsExpiresAt';
const LESSON_FIELDS = `id title level topic objective explanation sourceReference contentStatus mastery attempts nextReviewAt due
  exercises { ${EXERCISE_FIELDS} } audio { ${AUDIO_FIELDS} }`;

export const ECHO_HOME_FIELDS = `onboarded nextAction totalAttempts correctAttempts audioAttempts recommendedAudioLessonId recommendedAudioReason
  profile { goal selfReportedLevel languageCode diagnosticScore startingRoute }
  lessons { ${LESSON_FIELDS} }`;

export const CARD_FIELDS = 'id front back lessonId box nextReviewAt due missCount lastMiss';
