import React from 'react';

const SETTLED = 0.8;

/** "A1 · first sentences" → { band: "A1", stage: "First sentences" }; lessons without a stage share the band. */
export function stageOf(level) {
  const [band, stage] = (level || '').split('·').map(part => part.trim());
  return { band: band || 'Course', stage: stage ? stage[0].toUpperCase() + stage.slice(1) : band || 'Course' };
}

export function lessonState(lesson) {
  if (lesson.due && lesson.attempts > 0) return { id: 'due', label: 'Review ready' };
  if (lesson.mastery >= SETTLED) return { id: 'settled', label: 'Settled' };
  if (lesson.attempts > 0) return { id: 'started', label: `${Math.round(lesson.mastery * 100)}% deep` };
  return { id: 'new', label: 'New' };
}

/**
 * The course as one visible path, grouped by what you can do at each stage ("First words",
 * "First sentences" …). Everything is open: the path suggests an order but locks nothing.
 */
export default function CoursePath({ lessons, currentId, onOpen }) {
  const path = lessons.filter(lesson => !lesson.audio);
  if (!path.length) return null;
  const next = path.find(lesson => lessonState(lesson).id === 'due') || path.find(lesson => lesson.mastery < SETTLED) || path[0];
  const stages = [];
  path.forEach(lesson => {
    const { band, stage } = stageOf(lesson.level);
    const last = stages[stages.length - 1];
    if (last && last.stage === stage && last.band === band) last.lessons.push(lesson);
    else stages.push({ band, stage, lessons: [lesson] });
  });
  const current = stages.find(group => group.lessons.includes(next));
  const settled = path.filter(lesson => lesson.mastery >= SETTLED).length;

  return <section className="course-path panel" aria-labelledby="path-title">
    <div className="path-heading">
      <div><p className="eyebrow">YOUR COURSE</p><h2 id="path-title">{current ? `You’re at: ${current.stage}` : 'Your course'}</h2>
        <p>{settled} of {path.length} lessons settled. Short lessons, about five minutes each. Nothing is locked; the order is a suggestion.</p></div>
      <button type="button" onClick={() => onOpen(next.id)}>{next.attempts ? 'Continue' : 'Start'}: {next.title}</button>
    </div>
    <ol className="path-stages">
      {stages.map(group => <li key={`${group.band}-${group.stage}`} className="path-stage">
        <span className="stage-name"><b>{group.stage}</b><small>{group.band}</small></span>
        <ol className="path-lessons">
          {group.lessons.map(lesson => {
            const state = lessonState(lesson);
            return <li key={lesson.id}>
              <button type="button" className={`path-lesson ${state.id}${lesson.id === currentId ? ' current' : ''}`}
                aria-current={lesson.id === currentId ? 'step' : undefined} onClick={() => onOpen(lesson.id)}>
                <span className="path-dot" aria-hidden="true" />
                <span className="path-text"><b>{lesson.title}</b><small>{lesson.topic} · {state.label}</small></span>
              </button>
            </li>;
          })}
        </ol>
      </li>)}
    </ol>
  </section>;
}
