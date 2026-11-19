import React, { useEffect, useRef, useState } from 'react';
import { restClient } from '../app/http.js';
import { Button, Field, Notice, Panel, SectionHeading } from '../ui/index.jsx';
import { VOICE_LANGUAGES, speechRecognizer, talkHistory } from './workspaceTools.js';

// The voice room: talk with one AI role. The browser listens (speech to text), the role answers from its contract,
// and the browser reads the answer aloud (text to speech). Nothing said here can approve work; that stays on the board.

const api = restClient('/api/v1/workspace');

export default function VoiceRoom({ roles, onTasksChanged }) {
  const [roleId, setRoleId] = useState(roles[0]?.id || '');
  const [lang, setLang] = useState('en-US');
  const [turns, setTurns] = useState([]);
  const [heard, setHeard] = useState('');
  const [listening, setListening] = useState(false);
  const [thinking, setThinking] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [typed, setTyped] = useState('');
  const recognition = useRef(null);
  const Recognizer = speechRecognizer();
  const role = roles.find(r => r.id === roleId);

  // A new role starts a new conversation, and nobody keeps talking over it.
  useEffect(() => { setTurns([]); stopSpeaking(); }, [roleId]);
  useEffect(() => () => { recognition.current?.abort?.(); stopSpeaking(); }, []);

  function stopSpeaking() {
    if (typeof window !== 'undefined' && window.speechSynthesis) window.speechSynthesis.cancel();
    setSpeaking(false);
  }

  function speak(text) {
    if (typeof window === 'undefined' || !window.speechSynthesis || !text) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    const voice = window.speechSynthesis.getVoices().find(v => v.lang?.toLowerCase().startsWith(lang.slice(0, 2)));
    if (voice) utterance.voice = voice;
    utterance.onend = () => setSpeaking(false);
    utterance.onerror = () => setSpeaking(false);
    setSpeaking(true);
    window.speechSynthesis.speak(utterance);
  }

  async function send(text) {
    const said = text.trim();
    if (!said || thinking) return;
    stopSpeaking();
    const next = [...turns, { role: 'user', text: said }];
    setTurns(next); setHeard(''); setTyped(''); setThinking(true);
    try {
      const reply = await api(`/roles/${roleId}/talk`, { method: 'POST', body: { messages: talkHistory(next) }, timeoutMs: 120000 });
      setTurns(current => [...current, { role: 'assistant', text: reply.text, who: reply.roleName, tools: reply.toolsUsed }]);
      speak(reply.text);
      if (reply.toolsUsed?.some(name => name !== 'workspace_tasks')) onTasksChanged?.();
    } catch (e) {
      setTurns(current => [...current, { role: 'error', text: e.message }]);
    } finally {
      setThinking(false);
    }
  }

  function listen() {
    if (!Recognizer) return;
    if (listening) { recognition.current?.stop(); return; }
    stopSpeaking(); // interrupting: talking over the agent stops it
    const r = new Recognizer();
    r.lang = lang; r.interimResults = true; r.continuous = false;
    let finalText = '';
    r.onresult = event => {
      let interim = '';
      for (const result of event.results) {
        if (result.isFinal) finalText = result[0].transcript; else interim += result[0].transcript;
      }
      setHeard(finalText || interim);
    };
    r.onerror = event => { setListening(false); if (event.error !== 'aborted' && event.error !== 'no-speech') setTurns(c => [...c, { role: 'error', text: `Microphone: ${event.error}. Check the browser's microphone permission.` }]); };
    r.onend = () => { setListening(false); if (finalText) send(finalText); };
    recognition.current = r;
    setListening(true); setHeard('');
    r.start();
  }

  return <Panel className="voice-room">
    <SectionHeading eyebrow="VOICE ROOM" title={role ? `Talk with the ${role.name}` : 'Talk with your AI team'}
      lead="Tap the microphone and speak; tap again or pause to send. The answer is read aloud. Agents can propose tasks here, but only you approve them on the board." />
    <div className="voice-roles" role="group" aria-label="Who to talk to">
      {roles.map(r => <button key={r.id} type="button" aria-pressed={r.id === roleId} onClick={() => setRoleId(r.id)}>
        <span className="voice-avatar" aria-hidden="true">{r.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()}</span>{r.name}</button>)}
    </div>
    <div className="voice-controls">
      <Field label="Language" as="select" value={lang} onChange={e => setLang(e.target.value)}>
        {VOICE_LANGUAGES.map(l => <option key={l.id} value={l.id}>{l.label}</option>)}
      </Field>
      <button type="button" className={`voice-mic${listening ? ' live' : ''}`} onClick={listen} disabled={!Recognizer || thinking}
        aria-pressed={listening} aria-label={listening ? 'Stop listening and send' : 'Start talking'}>
        <span aria-hidden="true">{listening ? '■' : '🎙'}</span>{listening ? 'Listening… tap to send' : thinking ? `${role?.name || 'Agent'} is thinking…` : 'Tap to talk'}
      </button>
      {speaking && <Button variant="quiet" onClick={stopSpeaking}>Stop speaking</Button>}
    </div>
    {!Recognizer && <Notice>This browser can’t listen (Chrome, Edge and Safari can). You can still type below, and answers are read aloud.</Notice>}
    {heard && <p className="voice-heard" aria-live="polite">“{heard}”</p>}
    <ol className="voice-log" aria-live="polite">
      {turns.map((t, i) => <li key={i} className={`voice-turn ${t.role}`}>
        <b>{t.role === 'user' ? 'You' : t.role === 'error' ? 'Problem' : t.who}</b>
        <p>{t.text}</p>
        {t.tools?.length > 0 && <small>Used: {t.tools.join(', ')}</small>}
        {t.role === 'assistant' && <button type="button" className="link" onClick={() => speak(t.text)}>Play again</button>}
      </li>)}
    </ol>
    <form className="voice-type" onSubmit={e => { e.preventDefault(); send(typed); }}>
      <Field label="Or type" value={typed} onChange={e => setTyped(e.target.value)} placeholder={`Ask the ${role?.name || 'agent'}…`} maxLength={2000} />
      <Button type="submit" disabled={thinking || !typed.trim()}>Send</Button>
    </form>
    <p className="muted">What you say is turned into text by your browser; the text and the task board go to the AI model this server uses, to answer you.</p>
  </Panel>;
}
