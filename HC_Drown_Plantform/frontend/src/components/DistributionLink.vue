<template>
  <a class="distribution-link" :href="distributionUrl" aria-label="进入黄村乡监控分发系统" title="进入监控分发系统">
    <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4m-4-11 3 3 5-5"/></svg>
    <span>监控分发系统</span>
  </a>
</template>

<script setup lang="ts">
// Keep the current host for both localhost and LAN access; never pass auth tokens.
const safetyUrl = new URL(window.location.href);
safetyUrl.search = '';
safetyUrl.hash = '/';

const defaultDistributionUrl = new URL('/platform/', window.location.origin);
defaultDistributionUrl.port = '8000';

// Optional deployment override, e.g. a reverse proxy path on the same host.
const target = new URL(
  import.meta.env.VITE_DISTRIBUTION_URL || defaultDistributionUrl.href,
  window.location.origin
);
target.searchParams.set('returnTo', safetyUrl.href);
const distributionUrl = target.href;
</script>

<style scoped>
.distribution-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 9px 12px;
  min-height: 34px;
  flex-shrink: 0;
  border: 1px solid #3a6a83;
  border-radius: 5px;
  background: #15354b;
  color: #b6eafa;
  font-size: 11px;
  text-decoration: none;
  white-space: nowrap;
  transition: background .2s, border-color .2s;
}
.distribution-link:hover { background: #1d4c65; border-color: #73cee8; }
@media (max-width: 800px) {
  .distribution-link { width: 32px; min-height: 32px; padding: 7px; }
  .distribution-link span { display: none; }
}
</style>
