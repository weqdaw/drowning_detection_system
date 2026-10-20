import axios from 'axios';

const request = axios.create({
  baseURL: '',
  timeout: 10000
});

// Request Interceptor
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const tokenHead = localStorage.getItem('tokenHead') || 'Bearer ';
    if (token && config.headers) {
      config.headers['Authorization'] = `${tokenHead}${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code === 401) {
      localStorage.clear();
      window.location.hash = '#/login';
    }
    return res;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.clear();
      window.location.hash = '#/login';
    }
    return Promise.reject(error);
  }
);

export default request;
