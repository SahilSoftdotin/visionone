/*
 * Sign-in failure: mark both fields, and clear the mark the moment the person starts fixing it.
 *
 * Keycloak marks only the USERNAME wrapper with pf-m-error after a failed sign-in, and leaves the
 * password input group untouched. But the realm does not say which of the two was wrong - the
 * message is deliberately "invalid username or password", because saying which one tells an
 * attacker whether an account exists. So highlighting one field and not the other points at the
 * username as if we knew, which we do not. Both get marked, or neither would be honest.
 *
 * On "clear it when the credentials are right": nothing in the browser can know they are right -
 * that is the server's answer to the next submit. What it can know is that the person has started
 * correcting the entry, which is the moment a stale error stops being useful and starts being
 * noise. So the mark clears on first edit of either field, together with the message, rather than
 * sitting there red while someone types the correct password into it.
 *
 * Progressive enhancement: the CSS styles pf-m-error on its own, so with JavaScript off the
 * username still highlights. This adds the password and the clearing behaviour.
 */
(function () {
  'use strict';

  // Keycloak injects theme scripts into <head> with no defer, so this runs before the form has
  // been parsed. Without waiting, every getElementById below returns null and the whole thing
  // returns early - which is exactly what it did on the first attempt: the CSS half of the error
  // state worked and the JavaScript half silently did nothing.
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  function init() {
  var form = document.getElementById('kc-form-login');
  if (!form) {
    return;
  }

  var username = document.getElementById('username');
  var password = document.getElementById('password');

  // Did this page come back from a rejected attempt? Keycloak's own error markup is the signal;
  // it is not inferred from the URL, which would also be true on a fresh visit after a failure.
  var failed = document.querySelector(
    '.pf-v5-c-form-control.pf-m-error, .pf-v5-c-helper-text__item.pf-m-error');
  if (!failed) {
    return;
  }

  var wrappers = [];
  if (username) {
    // The username sits in a plain form-control wrapper.
    wrappers.push(username.closest('.pf-v5-c-form-control'));
  }
  if (password) {
    // The password sits in an input-group, because of the visibility toggle beside it. That outer
    // group is what carries the border, so that is what has to carry the error colour.
    wrappers.push(password.closest('.pf-v5-c-input-group') || password.closest('.pf-v5-c-form-control'));
  }
  wrappers = wrappers.filter(Boolean);

  wrappers.forEach(function (w) {
    w.classList.add('vo-invalid');
  });

  var messages = Array.prototype.slice.call(
    document.querySelectorAll('.pf-v5-c-helper-text__item.pf-m-error'))
    .map(function (el) {
      return el.closest('.pf-v5-c-form__helper-text') || el;
    })
    .filter(Boolean);

  function clear() {
    wrappers.forEach(function (w) {
      w.classList.remove('vo-invalid');
      w.classList.remove('pf-m-error');
    });
    [username, password].forEach(function (input) {
      if (input) {
        input.removeAttribute('aria-invalid');
      }
    });
    // The message goes with the colour. Leaving "invalid username or password" under a field that
    // is no longer red is the same stale state in a different place.
    messages.forEach(function (el) {
      el.hidden = true;
    });
    [username, password].forEach(function (input) {
      if (input) {
        input.removeEventListener('input', clear);
      }
    });
  }

  [username, password].forEach(function (input) {
    if (input) {
      input.addEventListener('input', clear);
    }
  });
  }
})();
