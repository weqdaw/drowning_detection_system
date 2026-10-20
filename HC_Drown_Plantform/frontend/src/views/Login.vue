<template>
  <div class="login-wrapper">
    <div class="glow-blob glow-blob-1"></div>
    <div class="glow-blob glow-blob-2"></div>

    <section class="login-intro" aria-label="黄村乡水域安全指挥平台">
      <div class="intro-brand">
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 3 27 7v8c0 7-5 11-11 14C10 26 5 22 5 15V7Z"/><path d="M10 15c3-5 9 5 12 0M10 20c3-5 9 5 12 0"/></svg>
        <div>黄村乡防溺水安全监管系统<span>HUANGCUN WATER SAFETY</span></div>
      </div>
      <div class="intro-copy">
        <span class="eyebrow">INTELLIGENT WATER PROTECTION</span>
        <h2>全域感知。<br>守护每一份<span>平安。</span></h2>
        <p>以智能感知连接水域安全，<br>构建监测、预警与救援协同的指挥视野。</p>
        <div class="intro-capabilities"><span>三维态势感知</span><span>AI 风险预警</span><span>应急协同处置</span></div>
      </div>
      <svg class="water-visual" viewBox="0 0 660 350" fill="none" aria-hidden="true">
        <defs>
          <linearGradient id="water-flow" x1="50" y1="300" x2="610" y2="40" gradientUnits="userSpaceOnUse"><stop stop-color="#52c9ee" stop-opacity=".04"/><stop offset=".5" stop-color="#52c9ee" stop-opacity=".6"/><stop offset="1" stop-color="#52c9ee" stop-opacity=".04"/></linearGradient>
          <radialGradient id="water-aura"><stop stop-color="#2586ad" stop-opacity=".2"/><stop offset="1" stop-color="#2586ad" stop-opacity="0"/></radialGradient>
        </defs>
        <ellipse cx="350" cy="180" rx="270" ry="160" fill="url(#water-aura)"/>
        <g stroke="#315370" stroke-opacity=".35">
          <path d="M30 210 305 60l325 145M30 250 305 100l325 145M30 290 305 140l325 145M75 315 350 165l280 125M120 335 395 185l235 105"/>
          <path d="m90 180 280 150m-220-180 280 150m-220-185 280 150m-220-185 280 150m-220-185 280 150m-220-180 280 150"/>
        </g>
        <g stroke="url(#water-flow)">
          <path d="M35 260c95-8 90-100 185-91s88 70 166 26S450 55 625 68" stroke-width="2"/>
          <path d="M35 276c95-8 90-100 185-91s88 70 166 26S450 71 625 84" stroke-width="1.5"/>
          <path d="M35 292c95-8 90-100 185-91s88 70 166 26S450 87 625 100"/>
          <path d="M35 244c95-8 90-100 185-91s88 70 166 26S450 39 625 52"/>
        </g>
        <g transform="translate(365 188)">
          <ellipse rx="93" ry="44" stroke="#52c9ee" stroke-opacity=".13"/><ellipse rx="66" ry="31" stroke="#52c9ee" stroke-opacity=".24"/><ellipse rx="36" ry="17" stroke="#52c9ee" stroke-opacity=".5"/>
          <path d="M0 0v-57" stroke="#52c9ee" stroke-dasharray="3 4"/>
          <circle cy="-62" r="5" fill="#80ddf8"/><circle cy="-62" r="11" stroke="#52c9ee" stroke-opacity=".35"/>
          <circle r="4" fill="#52c9ee"/>
        </g>
        <g fill="#7ccce8"><circle cx="190" cy="181" r="3"/><circle cx="482" cy="107" r="3"/><circle cx="550" cy="71" r="3"/></g>
        <g fill="#6e9ab8" font-size="8" font-family="monospace" letter-spacing="2"><text x="388" y="128">WATER SAFETY</text><text x="60" y="322">SPATIAL INTELLIGENCE / HUANGCUN</text></g>
      </svg>
      <div class="intro-footer">黄村乡 · 智慧水域安全管理平台 <span>感知 / 预警 / 联动</span></div>
    </section>

    <div class="card-container">
      <div class="login-card-kicker"><span></span> COMMAND CENTER ACCESS</div>
      <div class="header">
        <h1>黄村乡 AI 防溺水系统</h1>
        <p>{{ isLogin ? '后台决策与联动指挥中心' : '注册为防溺水安全应急责任人' }}</p>
      </div>

      <div class="tabs">
        <button class="tab-btn" :class="{ active: isLogin }" @click="isLogin = true">用户登录</button>
        <button class="tab-btn" :class="{ active: !isLogin }" @click="isLogin = false">新客注册</button>
      </div>

      <!-- LOGIN FORM -->
      <form v-if="isLogin" @submit.prevent="handleLogin">
        <div class="form-group">
          <label>账户账号</label>
          <input type="text" v-model="loginForm.username" class="form-control" placeholder="请输入用户名" required>
        </div>
        <div class="form-group">
          <label>登录密码</label>
          <input type="password" v-model="loginForm.password" class="form-control" placeholder="请输入登录密码" required>
        </div>
        <button type="submit" class="btn-submit">立即登录</button>
      </form>

      <!-- REGISTER FORM -->
      <form v-else @submit.prevent="handleRegister">
        <div class="form-group">
          <label>账号名称</label>
          <input type="text" v-model="regForm.username" class="form-control" placeholder="用于系统登录" required>
        </div>
        <div class="form-group">
          <label>安全密码</label>
          <input type="password" v-model="regForm.password" class="form-control" placeholder="长度不小于6位" required>
        </div>
        <div class="form-group">
          <label>真实姓名</label>
          <input type="text" v-model="regForm.realName" class="form-control" placeholder="方便值班室联络" required>
        </div>
        <div class="form-group">
          <label>手机号码</label>
          <input type="tel" v-model="regForm.phone" class="form-control" placeholder="接收出警短信及拨打电话">
        </div>
        <div class="form-group">
          <label>注册系统角色</label>
          <select v-model="regForm.roleCode" class="form-control" required>
            <option value="ROLE_OPERATOR">监控值守员 (Operator)</option>
            <option value="ROLE_RESCUER">现场救生员 (Lifeguard)</option>
            <option value="ROLE_LEADER">乡镇级领导 (Leader)</option>
            <option value="ROLE_ADMIN">系统运维管理员 (Admin)</option>
          </select>
        </div>
        <button type="submit" class="btn-submit">提交注册</button>
      </form>
      <div class="login-card-footer">水域安全监管 · 统一身份认证</div>
    </div>

    <!-- Toast Notification -->
    <div class="toast" :class="{ show: toast.visible, error: toast.type === 'error', success: toast.type === 'success' }">
      {{ toast.message }}
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import request from '../utils/request';

export default defineComponent({
  name: 'Login',
  setup() {
    const router = useRouter();
    const isLogin = ref(true);

    const loginForm = reactive({
      username: '',
      password: ''
    });

    const regForm = reactive({
      username: '',
      password: '',
      realName: '',
      phone: '',
      roleCode: 'ROLE_OPERATOR'
    });

    const toast = reactive({
      visible: false,
      message: '',
      type: 'success'
    });

    const showToast = (msg: string, type: 'success' | 'error' = 'success') => {
      toast.message = msg;
      toast.type = type;
      toast.visible = true;
      setTimeout(() => {
        toast.visible = false;
      }, 3000);
    };

    const handleLogin = async () => {
      try {
        const res: any = await request.post('/api/v1/auth/login', loginForm);
        if (res && res.code === 200) {
          showToast('登录成功，正在进入三维控制台...', 'success');
          localStorage.setItem('token', res.data.token);
          localStorage.setItem('tokenHead', res.data.tokenHead);
          setTimeout(() => {
            router.push('/');
          }, 1000);
        } else {
          showToast(res.message || '登录失败，账号或密码错误', 'error');
        }
      } catch (e) {
        showToast('服务器连接异常，请稍后重试', 'error');
      }
    };

    const handleRegister = async () => {
      try {
        const res: any = await request.post('/api/v1/auth/register', regForm);
        if (res && res.code === 200) {
          showToast('注册成功！正在前往登录页...', 'success');
          setTimeout(() => {
            isLogin.value = true;
            loginForm.username = regForm.username;
            loginForm.password = '';
          }, 1200);
        } else {
          showToast(res.message || '注册失败，用户名可能重复', 'error');
        }
      } catch (e) {
        showToast('服务器连接异常，请稍后重试', 'error');
      }
    };

    onMounted(() => {
      const savedTheme = localStorage.getItem('theme') || 'light';
      if (savedTheme === 'dark') {
        document.documentElement.classList.add('dark-theme');
      } else {
        document.documentElement.classList.remove('dark-theme');
      }
    });

    return {
      isLogin,
      loginForm,
      regForm,
      toast,
      handleLogin,
      handleRegister
    };
  }
});
</script>

<style scoped>
.login-wrapper {
  background: var(--bg-dark);
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
  transition: background 0.3s;
}

.glow-blob {
  position: absolute;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.15) 0%, rgba(37, 99, 235, 0) 70%);
  z-index: 0;
  filter: blur(40px);
  animation: float 10s ease-in-out infinite alternate;
}
.glow-blob-1 { top: -100px; left: -100px; }
.glow-blob-2 { bottom: -100px; right: -100px; background: radial-gradient(circle, rgba(14, 165, 233, 0.15) 0%, rgba(14, 165, 233, 0) 70%); }

@keyframes float {
  0% { transform: translateY(0) scale(1); }
  100% { transform: translateY(20px) scale(1.1); }
}

.card-container {
  width: 420px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 40px;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05);
  z-index: 10;
  color: var(--text-white);
  transition: all 0.3s;
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.header h1 {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin-bottom: 8px;
  background: linear-gradient(to right, #38bdf8, #2563eb);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header p {
  color: var(--text-muted);
  font-size: 14px;
}

.tabs {
  display: flex;
  background: var(--bg-dark);
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 25px;
  border: 1px solid var(--border-color);
}

.tab-btn {
  flex: 1;
  padding: 10px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
}

.tab-btn.active {
  background: #2563eb;
  color: #f8fafc;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.form-control {
  width: 100%;
  padding: 12px 16px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-white);
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
}

.form-control:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

select.form-control {
  appearance: none;
  background-image: url("data:image/svg+xml;utf8,<svg fill='%2394a3b8' height='24' viewBox='0 0 24 24' width='24' xmlns='http://www.w3.org/2000/svg'><path d='M7 10l5 5 5-5z'/><path d='M0 0h24v24H0z' fill='none'/></svg>");
  background-repeat: no-repeat;
  background-position: right 12px center;
}

.btn-submit {
  width: 100%;
  padding: 14px;
  background: #2563eb;
  border: none;
  border-radius: 10px;
  color: #f8fafc;
  font-weight: 600;
  font-size: 15px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.4);
  margin-top: 10px;
}

.btn-submit:hover {
  background: #1d4ed8;
  transform: translateY(-1px);
}

/* Access screen shares the command-center palette without changing auth behavior. */
.login-wrapper { min-height: 100dvh; height: 100dvh; overflow-y: auto; padding: 56px clamp(40px,7vw,140px); gap: clamp(60px,9vw,180px); background: radial-gradient(ellipse at 25% 60%,#10273b 0,transparent 55%),#070e18; }
.login-wrapper::before { content: ''; position: absolute; inset: 0; background-image: linear-gradient(#7baad605 1px,transparent 1px),linear-gradient(90deg,#7baad605 1px,transparent 1px); background-size: 64px 64px; pointer-events: none; }
.glow-blob { animation: none; opacity: .3; pointer-events: none; }
.login-intro { flex: 1; max-width: 720px; min-width: 0; min-height: 700px; align-self: stretch; display: flex; flex-direction: column; justify-content: center; position: relative; z-index: 1; }
.intro-brand { display: flex; align-items: center; gap: 12px; font-size: 17px; letter-spacing: 1px; margin-bottom: 64px; }
.intro-brand svg { width: 43px; height: 43px; fill: none; stroke: var(--primary); stroke-width: 1.4; flex-shrink: 0; }
.intro-brand span { display: block; font-size: 9px; letter-spacing: 2.5px; color: #7698b5; margin-top: 7px; }
.intro-copy { position: relative; z-index: 1; }
.intro-copy .eyebrow { font-size: 9px; letter-spacing: 3px; color: #6da8c6; }
.intro-copy h2 { font-size: clamp(34px,3.4vw,60px); font-weight: 400; line-height: 1.5; letter-spacing: 3px; margin: 20px 0; }
.intro-copy h2 span { color: #7cdaef; }
.intro-copy p { font-size: 13px; line-height: 2; color: #8eabc1; letter-spacing: .8px; }
.intro-capabilities { display: flex; gap: 0; margin-top: 28px; color: #bad1e0; font-size: 11px; letter-spacing: 1px; }
.intro-capabilities span + span { border-left: 1px solid #31516b; margin-left: 18px; padding-left: 18px; }
.water-visual { width: 100%; max-height: 300px; margin-top: -5px; flex-shrink: 0; }
.intro-footer { display: flex; align-items: center; justify-content: space-between; border-top: 1px solid #25394a; padding-top: 18px; font-size: 9px; letter-spacing: 1px; color: #66859c; }
.intro-footer span { color: #8ca8bb; }
.card-container { width: 420px; flex-shrink: 0; padding: 36px; border-radius: 12px; background: linear-gradient(145deg,#132337ed,#0b1726f5); border-color: #2d455c; box-shadow: 0 28px 100px #0005,inset 0 1px 0 #6d9bbd1a; margin: auto 0; position: relative; }
.card-container::before { content: ''; position: absolute; top: -1px; left: 36px; right: 36px; height: 1px; background: linear-gradient(90deg,transparent,#68c7e6,transparent); }
.login-card-kicker { display: flex; align-items: center; gap: 8px; color: #7196b3; font-size: 9px; letter-spacing: 2px; margin-bottom: 28px; }
.login-card-kicker span { width: 5px; height: 5px; border: 1px solid #7ddff8; transform: rotate(45deg); }
.header { text-align: left; margin-bottom: 26px; }
.header h1 { font-size: 22px; font-weight: 500; letter-spacing: .6px; background: none; -webkit-text-fill-color: #e2edf8; margin-bottom: 10px; }
.header p { font-size: 11px; color: #86a1b9; letter-spacing: .5px; }
.tabs { padding: 0; border: 0; border-bottom: 1px solid #2a3f54; border-radius: 0; background: transparent; margin-bottom: 26px; gap: 24px; }
.tab-btn { flex: none; border-radius: 0; padding: 0 0 13px; position: relative; font-size: 13px; }
.tab-btn.active { background: transparent; color: #84dff7; box-shadow: none; }
.tab-btn.active::after { content: ''; position: absolute; bottom: -1px; left: 0; right: 0; height: 2px; background: var(--primary); }
.form-group { margin-bottom: 18px; }
.form-group label { font-size: 11px; color: #a3bad0; font-weight: 400; letter-spacing: .4px; }
.form-control { border-radius: 5px; padding: 12px 14px; font-size: 12px; border-color: #2d4359; }
.form-control:focus { border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-glow); }
.btn-submit { border-radius: 5px; background: linear-gradient(110deg,#216d91,#215b82); border: 1px solid #3684a6; font-size: 13px; font-weight: 500; letter-spacing: 2px; box-shadow: 0 5px 18px #062c4c4d; margin-top: 8px; }
.btn-submit:hover { background: #297ea2; transform: none; border-color: var(--primary); }
.login-card-footer { border-top: 1px solid #253b50; margin-top: 28px; padding-top: 18px; text-align: center; color: #6d8aa3; font-size: 9px; letter-spacing: 1px; }
@media (max-height: 850px) and (min-width: 1001px) { .login-wrapper { padding-top: 28px; padding-bottom: 28px; }.login-intro { min-height: 600px; }.intro-brand { margin-bottom: 35px; }.water-visual { max-height: 230px; } }
@media (max-width: 1200px) { .login-wrapper { padding: 36px 44px; gap: 40px; }.intro-brand { font-size: 14px; }.card-container { width: 380px; padding: 30px; }.intro-copy h2 { font-size: 38px; }.intro-footer span { display: none; } }
@media (max-width: 1000px) { .login-wrapper { padding: 36px 24px; display: block; }.login-intro { max-width: 440px; min-height: 0; margin: 0 auto; }.intro-brand { margin-bottom: 26px; }.intro-copy, .water-visual, .intro-footer { display: none; }.card-container { width: 100%; max-width: 440px; margin: 0 auto; }.login-wrapper::before { position: fixed; } }
@media (max-width: 440px) { .login-wrapper { padding: 28px 16px; }.intro-brand { font-size: 12px; }.intro-brand span { font-size: 8px; }.card-container { padding: 28px 24px; }.header h1 { font-size: 20px; } }
</style>
