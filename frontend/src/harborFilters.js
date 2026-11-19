/*
 * Rules for Harbor's job filters, kept out of the JSX so they can be tested with node:test.
 */

/** The Nordic countries and Switzerland, the countries Harbor has work-permit guides for. */
export const FOCUS_COUNTRIES = ['DK', 'SE', 'NO', 'CH'];
export const NORDICS_AND_SWISS = FOCUS_COUNTRIES.join(',');

/**
 * The visa filter a person starts with. People who need a work permit start by hiding only postings that
 * refuse sponsorship: most postings never mention visas, so "open to me" would hide nearly every job.
 */
export function defaultVisaFilter(citizenship) {
  return citizenship === 'OTHER' ? 'NOT_RULED_OUT' : 'ANY';
}

/**
 * The choices for the country filter. jobsByCountry holds every country Harbor has postings for, each
 * counted with the person's other filters (0 when those hide them all). The person's own target countries
 * come first as one choice, and a country already chosen always stays in the list.
 */
export function countryOptions({ names = {}, jobsByCountry = {}, targetCountries = [], selected = 'ANY' }) {
  const count = codes => codes.reduce((sum, code) => sum + (jobsByCountry[code] || 0), 0);
  const label = code => names[code] || code;
  const options = [{ value: 'ANY', label: 'Any country' }];
  const mine = targetCountries.filter(code => code in jobsByCountry);
  if (mine.length > 1) options.push({ value: mine.join(','), label: `My countries: ${mine.map(label).join(', ')} (${count(mine)})` });
  options.push({ value: NORDICS_AND_SWISS, label: `Nordics + Switzerland (${count(FOCUS_COUNTRIES)})` });
  const codes = Object.keys(jobsByCountry).sort((a, b) => label(a).localeCompare(label(b)));
  if (selected && !selected.includes(',') && selected !== 'ANY' && !codes.includes(selected)) codes.push(selected);
  codes.forEach(code => options.push({ value: code, label: `${label(code)} (${jobsByCountry[code] || 0})` }));
  if (selected && selected.includes(',') && !options.some(option => option.value === selected))
    options.push({ value: selected, label: selected.split(',').map(label).join(', ') });
  return options;
}

/**
 * The employers to show above the results. With a country chosen, only employers with postings that pass
 * the filters stay, plus careers-link-only employers based in that country.
 */
export function visibleCompanies(companies, counts, country) {
  if (!country || country === 'ANY') return companies;
  const codes = country.split(',');
  return companies.filter(company => company.connected ? (counts?.[company.id] || 0) > 0 : codes.includes(company.home));
}

/** The line above the results, for example "Showing 100 of 451 matching postings (901 in Harbor)". */
export function resultCountText({ shown, matched, total }) {
  if (!matched) return `No postings match your filters (${total} in Harbor).`;
  const head = shown < matched ? `Showing ${shown} of ${matched} matching postings` : `${matched} matching ${matched === 1 ? 'posting' : 'postings'}`;
  return `${head} (${total} in Harbor), best fit first.`;
}
