<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { uploadImage } from '@/api/upload'
import {
  getUserProfile,
  updateAvatar,
  updateName,
  updatePassword,
  updateSign,
  type UpdatePasswordPayload,
} from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const savingName = ref(false)
const savingSign = ref(false)
const savingAvatar = ref(false)
const uploadingAvatar = ref(false)
const savingPassword = ref(false)
const errorText = ref('')
const successText = ref('')

const profileForm = reactive({
  name: '',
  sign: '',
  avatarUrl: '',
})

const passwordForm = reactive<UpdatePasswordPayload>({
  oldPassword: '',
  newPassword: '',
})

async function loadProfile() {
  if (authStore.state.userId === null) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const data = await getUserProfile(authStore.state.userId)
    profileForm.name = data.name || ''
    profileForm.sign = data.sign || ''
    profileForm.avatarUrl = data.avatarUrl || ''
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载资料失败'
  } finally {
    loading.value = false
  }
}

async function saveName() {
  savingName.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updateName({ name: profileForm.name.trim() })
    successText.value = '用户名已更新'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '更新用户名失败'
  } finally {
    savingName.value = false
  }
}

async function saveSign() {
  savingSign.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updateSign({ sign: profileForm.sign.trim() })
    successText.value = '个性签名已更新'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '更新签名失败'
  } finally {
    savingSign.value = false
  }
}

async function saveAvatar() {
  savingAvatar.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updateAvatar({ avatarUrl: profileForm.avatarUrl.trim() })
    successText.value = '头像已更新'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '更新头像失败'
  } finally {
    savingAvatar.value = false
  }
}

async function handleAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploadingAvatar.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const uploaded = await uploadImage('user_avatar', file)
    profileForm.avatarUrl = uploaded.url
    successText.value = '头像上传成功，点击保存后生效'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '上传头像失败'
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    errorText.value = '请输入旧密码和新密码'
    return
  }
  savingPassword.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    successText.value = '密码已更新'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '修改密码失败'
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="settings-layout">
    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div class="settings-grid">
      <aside class="settings-side">
        <button class="back-link" @click="router.push('/me')">返回个人主页</button>
        <section class="side-card">
          <button class="side-item active">个人资料</button>
          <button class="side-item">账号设置</button>
        </section>
      </aside>

      <main class="settings-main">
        <div v-if="loading" class="panel">正在加载资料...</div>
        <div v-else class="settings-sections">
          <section class="panel profile-panel">
            <div class="panel-head">
              <div>
                <p class="section-label">个人资料</p>
                <h1>基础信息</h1>
              </div>
            </div>

            <div class="profile-grid">
              <div class="form-stack">
                <label class="field">
                  <span>用户名</span>
                  <input v-model.trim="profileForm.name" maxlength="64" />
                  <button :disabled="savingName" @click="saveName">{{ savingName ? '保存中...' : '保存用户名' }}</button>
                </label>

                <label class="field">
                  <span>个性签名</span>
                  <textarea v-model.trim="profileForm.sign" maxlength="128" rows="4" />
                  <button :disabled="savingSign" @click="saveSign">{{ savingSign ? '保存中...' : '保存签名' }}</button>
                </label>
              </div>

              <section class="avatar-panel">
                <div class="avatar-box">
                  <img v-if="profileForm.avatarUrl" class="avatar-preview" :src="profileForm.avatarUrl" alt="avatar preview" />
                  <div v-else class="avatar-fallback">{{ (profileForm.name || authStore.state.account || 'J').slice(0, 1).toUpperCase() }}</div>
                </div>
                <p>支持 JPG、PNG、JPEG，本地上传后点击保存即可。</p>
                <input type="file" accept="image/*" :disabled="uploadingAvatar" @change="handleAvatarFileChange" />
                <input v-model.trim="profileForm.avatarUrl" maxlength="512" placeholder="或直接粘贴头像链接" />
                <button :disabled="savingAvatar" @click="saveAvatar">{{ savingAvatar ? '保存中...' : '保存头像' }}</button>
              </section>
            </div>
          </section>

          <section class="panel password-panel">
            <div class="panel-head">
              <div>
                <p class="section-label">账号设置</p>
                <h2>修改密码</h2>
              </div>
            </div>
            <div class="password-grid">
              <label class="field">
                <span>旧密码</span>
                <input v-model="passwordForm.oldPassword" type="password" maxlength="64" />
              </label>
              <label class="field">
                <span>新密码</span>
                <input v-model="passwordForm.newPassword" type="password" maxlength="64" />
              </label>
            </div>
            <button :disabled="savingPassword" @click="savePassword">{{ savingPassword ? '保存中...' : '修改密码' }}</button>
          </section>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.settings-layout { display: grid; gap: 16px; }
.settings-grid { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 18px; align-items: start; }
.settings-side { display: grid; gap: 14px; position: sticky; top: 92px; }
.back-link, .side-item, .field button, .avatar-panel button, .password-panel button { border: none; font: inherit; }
.back-link { border-radius: 14px; padding: 14px 16px; background: #fff; color: var(--ink-main); box-shadow: inset 0 0 0 1px var(--line-soft); cursor: pointer; text-align: left; }
.side-card, .panel { border: 1px solid var(--line-soft); border-radius: 18px; background: #fff; }
.side-card { padding: 12px; display: grid; gap: 8px; }
.side-item { border-radius: 12px; background: transparent; color: var(--ink-main); text-align: left; padding: 12px 14px; cursor: pointer; }
.side-item.active { background: #f4f8ff; color: var(--brand); font-weight: 700; }
.settings-main, .settings-sections { display: grid; gap: 16px; }
.panel { padding: 20px; }
.panel-head h1, .panel-head h2 { margin: 0; color: var(--ink-strong); }
.section-label { margin: 0 0 8px; color: var(--brand); font-size: 0.85rem; font-weight: 700; }
.profile-grid { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 20px; margin-top: 18px; }
.form-stack { display: grid; gap: 16px; }
.field { display: grid; gap: 8px; }
.field span { color: var(--ink-main); font-weight: 600; }
.field input, .field textarea, .avatar-panel input { border: 1px solid var(--line-soft); border-radius: 12px; background: #f7f8fa; padding: 12px 14px; color: var(--ink-strong); }
.field textarea { resize: vertical; }
.field button, .avatar-panel button, .password-panel button { justify-self: start; border-radius: 12px; padding: 10px 16px; background: linear-gradient(135deg, #1e80ff, #5ea1ff); color: #fff; cursor: pointer; }
.avatar-panel { display: grid; gap: 12px; align-content: start; }
.avatar-box { width: 180px; height: 180px; border-radius: 28px; overflow: hidden; background: linear-gradient(135deg, #f7d79b, #f39db9 55%, #93c7ff); display: grid; place-items: center; color: #24324a; }
.avatar-preview, .avatar-fallback { width: 100%; height: 100%; }
.avatar-preview { object-fit: cover; }
.avatar-fallback { display: grid; place-items: center; font-size: 3rem; font-weight: 700; }
.avatar-panel p { margin: 0; color: var(--ink-muted); line-height: 1.6; }
.password-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin: 18px 0 16px; }
.error-text, .success-text { margin: 0; }
.error-text { color: var(--danger); }
.success-text { color: var(--ok); }
@media (max-width: 980px) { .settings-grid { grid-template-columns: 1fr; } .settings-side { position: static; } .profile-grid, .password-grid { grid-template-columns: 1fr; } }
</style>
