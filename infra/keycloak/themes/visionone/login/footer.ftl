<#--
  The footer under the sign-in card.

  This is the one template this theme overrides, and it is the one template where that is the
  intended thing to do: base/login/footer.ftl is an empty macro whose own comment says "you can
  override this file in your custom theme to declare a custom login footer element". Empty
  upstream means there is nothing here to fall behind at the next Keycloak upgrade, which is the
  objection to forking login.ftl or template.ftl and the reason theme.properties restyles rather
  than re-implements.

  It replaces a CSS ::after pseudo-element that carried the same sentence. That worked for text
  and could never work here: generated content cannot hold a link, and the whole point of a
  footer on a sign-in page someone has been sent to is that it proves the page belongs to a real
  company - there is somewhere to click, and a privacy policy to read before typing a password in.

  Absolute URLs to the marketing site, deliberately. This page is served from
  app.visiondigitallab.com and the company site is visiondigitallab.com, so a relative path would
  resolve back into Keycloak and 404.
-->
<#macro content>
<footer class="vo-login-footer">
  <p class="vo-login-footer__brand">
    A <a href="https://visiondigitallab.com" rel="noopener">Vision Digital Lab</a> product
  </p>
  <ul class="vo-login-footer__links">
    <li><a href="https://visiondigitallab.com/privacy" rel="noopener">Privacy</a></li>
    <li><a href="https://visiondigitallab.com/terms" rel="noopener">Terms</a></li>
    <li><a href="https://visiondigitallab.com/security" rel="noopener">Security</a></li>
    <li><a href="mailto:hello@visiondigitallab.com">Contact</a></li>
  </ul>
</footer>
</#macro>
