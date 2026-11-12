<!-- Adapted from youlaitech/vue3-element-admin src/views/login/index.vue. -->
<template>
  <main class="login-page">
    <div class="login-container">
      <div class="login-layout">
        <section class="login-brand" aria-label="SuperPower 运营后台">
          <div class="login-brand__header">
            <span class="login-brand__logo" aria-hidden="true">S</span>
            <div class="login-brand__identity">
              <span class="login-brand__name">SuperPower</span>
              <span class="login-brand__version">运营管理后台</span>
            </div>
          </div>
          <div class="login-brand__hero">
            <span class="login-brand__eyebrow">配置管理中心</span>
            <h1 class="login-brand__title">核心配置，<br />统一管理。</h1>
            <p class="login-brand__desc">
              集中维护环境选项与平台站点，<br />让每一次调整清晰有序。
            </p>
          </div>
          <div class="login-brand__features">
            <span><el-icon><Setting /></el-icon>环境选项</span>
            <span><el-icon><Grid /></el-icon>平台站点</span>
            <span><el-icon><Clock /></el-icon>变更记录</span>
          </div>
        </section>
        <section class="login-card" aria-labelledby="login-title">
          <h2 id="login-title" class="login-card__title">欢迎回来</h2>
          <p class="login-card__desc">登录你的运营管理员账号</p>
          <el-alert
            v-if="error || route.query.expired"
            :title="error || '登录已过期，请重新登录。'"
            type="error"
            :closable="false"
            show-icon
            class="section-alert"
          />
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            hide-required-asterisk
            size="large"
            @submit.prevent="submit"
          >
            <el-form-item prop="username" label="管理员账号">
              <el-input
                v-model.trim="form.username"
                placeholder="请输入管理员账号"
                aria-label="管理员账号"
                :prefix-icon="User"
                autocomplete="username"
                maxlength="100"
              />
            </el-form-item>
            <el-form-item prop="password" label="密码">
              <el-input
                v-model="form.password"
                placeholder="请输入密码"
                aria-label="密码"
                type="password"
                show-password
                :prefix-icon="Lock"
                autocomplete="current-password"
                maxlength="256"
                @keyup="checkCaps"
              />
            </el-form-item>
            <p v-if="caps" class="caps-warning">大写锁定已开启</p>
            <el-button
              class="login-card__submit"
              :loading="loading"
              type="primary"
              native-type="submit"
            >登录</el-button>
          </el-form>
          <p class="login-card__notice">
            仅供运营人员使用<br />客户账号请在浏览器客户端登录
          </p>
        </section>
      </div>
      <footer class="login-footer">SuperPower Browser · 运营配置中心</footer>
    </div>
  </main>
</template>
<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Clock, Grid, Lock, Setting, User } from "@element-plus/icons-vue";
import type { FormInstance } from "element-plus";
import { errorMessage, login } from "../api";
const route = useRoute(),
  router = useRouter(),
  formRef = ref<FormInstance>();
const form = reactive({ username: "", password: "" }),
  loading = ref(false),
  error = ref(""),
  caps = ref(false);
const rules = {
  username: [{ required: true, trigger: "blur", message: "请输入管理员账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入密码" }],
};
function checkCaps(event: KeyboardEvent) {
  caps.value = event.getModifierState("CapsLock");
}
async function submit() {
  if (loading.value || !(await formRef.value?.validate().catch(() => false)))
    return;
  loading.value = true;
  error.value = "";
  try {
    await login(form.username, form.password);
    form.password = "";
    const redirect =
      typeof route.query.redirect === "string"
        ? route.query.redirect
        : "/overview";
    await router.replace(
      redirect.startsWith("/") &&
        !redirect.startsWith("//") &&
        !redirect.startsWith("/login")
        ? redirect
        : "/overview",
    );
  } catch (cause) {
    error.value = errorMessage(cause);
  } finally {
    loading.value = false;
  }
}
</script>
<style lang="scss" scoped>
.login-page {
  --el-color-primary: #3874e8;
  --el-color-primary-light-3: #6392ee;
  --el-color-primary-light-5: #9bb9f4;
  --el-color-primary-dark-2: #285dcc;
  display: grid;
  place-items: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 40px 24px;
  background:
    radial-gradient(ellipse at 15% 20%, #e6eefc 0%, transparent 45%),
    radial-gradient(ellipse at 85% 80%, #eaf0fa 0%, transparent 45%),
    #f5f7fb;
}
.login-container {
  width: 100%;
  max-width: 880px;
}
.login-layout {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  padding: 10px;
  background: #fff;
  border: 1px solid #e6ebf3;
  border-radius: 24px;
  box-shadow: 0 24px 70px -20px rgb(45 68 108 / 16%), 0 4px 16px rgb(45 68 108 / 3%);
}
.login-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
  padding: 34px 30px;
  overflow: hidden;
  background: linear-gradient(145deg, #f0f5ff 0%, #e8f0fd 100%);
  border-radius: 16px;
  &::after {
    position: absolute;
    right: -110px;
    bottom: 80px;
    width: 230px;
    height: 230px;
    content: "";
    pointer-events: none;
    border: 1px solid rgb(91 137 219 / 10%);
    border-radius: 50%;
    box-shadow: 0 0 0 36px rgb(91 137 219 / 3%), 0 0 0 72px rgb(91 137 219 / 2%);
  }
  &__header {
    display: flex;
    gap: 12px;
    align-items: center;
  }
  &__logo {
    display: grid;
    place-items: center;
    flex-shrink: 0;
    width: 42px;
    height: 42px;
    color: #fff;
    font-size: 25px;
    font-weight: 700;
    background: linear-gradient(145deg, #4c8af7, #3066d9);
    border-radius: 12px;
    box-shadow: 0 6px 12px rgb(56 116 232 / 16%);
  }
  &__identity {
    display: flex;
    flex-direction: column;
    gap: 5px;
    min-width: 0;
  }
  &__name {
    font-size: 20px;
    font-weight: 600;
    line-height: 1.2;
    letter-spacing: -0.5px;
    color: #20385e;
  }
  &__version {
    font-size: 12px;
    color: #647897;
    letter-spacing: 1px;
  }
  &__hero {
    z-index: 1;
    padding: 58px 0;
  }
  &__eyebrow {
    font-size: 12px;
    letter-spacing: 2px;
    color: #5378b4;
  }
  &__title {
    margin: 16px 0 18px;
    font-size: 34px;
    font-weight: 600;
    line-height: 1.5;
    letter-spacing: 1px;
    color: #223d65;
  }
  &__desc {
    margin: 0;
    font-size: 13px;
    line-height: 1.95;
    color: #647897;
  }
  &__features {
    z-index: 1;
    display: flex;
    flex-wrap: wrap;
    gap: 15px;
    align-items: center;
    color: #60799d;
    font-size: 11px;
    span {
      display: inline-flex;
      align-items: center;
      gap: 5px;
    }
    .el-icon {
      font-size: 14px;
    }
  }
}
.login-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  padding: 42px 46px;
  &__title {
    margin: 0;
    font-size: 26px;
    font-weight: 600;
    line-height: 1.4;
    color: #24334b;
  }
  &__desc {
    margin: 10px 0 30px;
    font-size: 13px;
    color: #7e8a9d;
  }
  &__submit {
    width: 100%;
    height: 46px;
    margin-top: 4px;
    font-size: 14px;
    font-weight: 500;
    border-radius: 8px;
    box-shadow: 0 5px 12px rgb(56 116 232 / 15%);
  }
  &__notice {
    margin: 24px 0 0;
    color: #8490a2;
    font-size: 12px;
    line-height: 1.8;
    text-align: center;
  }
}
:deep(.el-form-item) {
  margin-bottom: 24px;
}
:deep(.el-form-item__label) {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 20px;
  color: #4c5c73;
}
:deep(.el-input__wrapper) {
  height: 46px;
  padding: 0 14px;
  border-radius: 8px;
  background: #fafbfd;
  box-shadow: 0 0 0 1px #e0e6ef inset;
  &.is-focus {
    background: #fff;
    box-shadow: 0 0 0 1px var(--el-color-primary) inset, 0 0 0 3px rgb(56 116 232 / 9%);
  }
}
:deep(.el-input__prefix) {
  color: #8c9bb0;
}
:deep(.el-input__inner::placeholder) {
  color: #9ba7b7;
}
.login-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  line-height: 1.6;
  color: #8592a6;
}
.caps-warning {
  margin: -4px 0 12px;
  color: #ad7418;
  font-size: 12px;
}
@media (max-width: 760px) {
  .login-container {
    max-width: 440px;
  }
  .login-layout {
    grid-template-columns: 1fr;
  }
  .login-brand {
    padding: 22px 26px;
    &__hero,
    &__features,
    &::after {
      display: none;
    }
  }
  .login-card {
    padding: 30px 26px 24px;
  }
}
@media (max-width: 420px) {
  .login-page {
    padding: 24px 16px;
  }
  .login-layout {
    padding: 8px;
    border-radius: 20px;
  }
  .login-brand {
    padding: 20px;
  }
  .login-card {
    padding: 28px 20px 24px;
  }
}
</style>
