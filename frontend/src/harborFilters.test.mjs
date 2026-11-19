import test from 'node:test';
import assert from 'node:assert/strict';
import { countryOptions, defaultVisaFilter, resultCountText, visibleCompanies } from './harborFilters.js';

const names = { DK: 'Denmark', SE: 'Sweden', NO: 'Norway', CH: 'Switzerland', DE: 'Germany' };

test('a country stays in the list at 0 when the other filters hide its postings', () => {
  const options = countryOptions({ names, jobsByCountry: { DE: 31, DK: 0, SE: 2 } });
  assert.deepEqual(options.map(o => o.label), ['Any country', 'Nordics + Switzerland (2)', 'Denmark (0)', 'Germany (31)', 'Sweden (2)']);
});

test('the person\'s own countries come first as one choice', () => {
  const options = countryOptions({ names, jobsByCountry: { DK: 97, SE: 169, DE: 451 }, targetCountries: ['DK', 'SE', 'IS'] });
  assert.equal(options[1].value, 'DK,SE');
  assert.equal(options[1].label, 'My countries: Denmark, Sweden (266)');
});

test('a chosen country without postings is still offered, so the select never goes blank', () => {
  const options = countryOptions({ names, jobsByCountry: { DK: 4 }, selected: 'NO' });
  assert.ok(options.some(o => o.value === 'NO' && o.label === 'Norway (0)'));
});

test('people outside the EU/EEA and Switzerland start by hiding only postings that refuse sponsorship', () => {
  assert.equal(defaultVisaFilter('OTHER'), 'NOT_RULED_OUT');
  assert.equal(defaultVisaFilter('EU_EEA'), 'ANY');
  assert.equal(defaultVisaFilter('UNKNOWN'), 'ANY');
});

test('with a country chosen, only employers with postings there stay', () => {
  const companies = [
    { id: 'lego', connected: true, home: 'DK' }, { id: 'bosch', connected: true, home: 'DE' },
    { id: 'arla', connected: false, home: 'DK' }, { id: 'uber', connected: false, home: 'US' },
  ];
  assert.equal(visibleCompanies(companies, {}, 'ANY').length, 4);
  assert.deepEqual(visibleCompanies(companies, { lego: 30, bosch: 0 }, 'DK').map(c => c.id), ['lego', 'arla']);
});

test('the result line says how many match, not only how many are on screen', () => {
  assert.equal(resultCountText({ shown: 100, matched: 451, total: 901 }), 'Showing 100 of 451 matching postings (901 in Harbor), best fit first.');
  assert.equal(resultCountText({ shown: 1, matched: 1, total: 901 }), '1 matching posting (901 in Harbor), best fit first.');
  assert.equal(resultCountText({ shown: 0, matched: 0, total: 901 }), 'No postings match your filters (901 in Harbor).');
});
