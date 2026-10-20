import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import './assets/style.css';

// Configure Cesium Assets Base URL to load all WebWorkers/Styles from public CDN
window.CESIUM_BASE_URL = 'https://unpkg.com/cesium@1.118.0/Build/Cesium/';

const app = createApp(App);
app.use(router);
app.mount('#app');
