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
    errorText.value = error instanceof Error ? error.message : 'Failed to load profile'
  } finally {
    loading.value = false
  }
}

async function saveName() {
  savingName.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updateName({
      name: profileForm.name.trim(),
    })
    successText.value = 'Name updated successfully'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to update name'
  } finally {
    savingName.value = false
  }
}

async function saveSign() {
  savingSign.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updateSign({
      sign: profileForm.sign.trim(),
    })
    successText.value = 'Sign updated successfully'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to update sign'
  } finally {
    savingSign.value = false
  }
}

async function saveAvatar() {
  savingAvatar.value = true
  errorText.value = ''
  successText.value = ''
  try {
    await updateAvatar({
      avatarUrl: profileForm.avatarUrl.trim(),
    })
    successText.value = 'Avatar updated successfully'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to update avatar'
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
    successText.value = 'Avatar uploaded. Click save to persist.'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to upload avatar'
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

async function savePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    errorText.value = 'Old password and new password are required'
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
    successText.value = 'Password updated successfully'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to update password'
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <section class="settings-layout">
    <header class="settings-head">
      <h1>Settings</h1>
      <button @click="router.push('/me')">Back to Profile</button>
    </header>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div v-if="loading" class="panel">Loading profile...</div>
    <div v-else class="settings-grid">
      <section class="card">
        <h2>Name</h2>
        <label class="field">
          <span>Display Name</span>
          <input v-model.trim="profileForm.name" maxlength="64" />
        </label>
        <button :disabled="savingName" @click="saveName">
          {{ savingName ? 'Saving...' : 'Save Name' }}
        </button>
      </section>

      <section class="card">
        <h2>Sign</h2>
        <label class="field">
          <span>Signature</span>
          <textarea v-model.trim="profileForm.sign" maxlength="128" rows="3" />
        </label>
        <button :disabled="savingSign" @click="saveSign">
          {{ savingSign ? 'Saving...' : 'Save Sign' }}
        </button>
      </section>

      <section class="card">
        <h2>Avatar</h2>
        <img
          class="avatar-preview"
          :src="profileForm.avatarUrl || 'https://placehold.co/160x160?text=Avatar'"
          alt="avatar preview"
        />
        <label class="field">
          <span>Avatar URL</span>
          <input v-model.trim="profileForm.avatarUrl" maxlength="512" />
        </label>
        <div class="upload-row">
          <input
            type="file"
            accept="image/*"
            :disabled="uploadingAvatar"
            @change="handleAvatarFileChange"
          />
          <span>{{ uploadingAvatar ? 'Uploading...' : 'Upload local image' }}</span>
        </div>
        <button :disabled="savingAvatar" @click="saveAvatar">
          {{ savingAvatar ? 'Saving...' : 'Save Avatar' }}
        </button>
      </section>

      <section class="card">
        <h2>Password</h2>
        <label class="field">
          <span>Old Password</span>
          <input v-model="passwordForm.oldPassword" type="password" maxlength="64" />
        </label>
        <label class="field">
          <span>New Password</span>
          <input v-model="passwordForm.newPassword" type="password" maxlength="64" />
        </label>
        <button :disabled="savingPassword" @click="savePassword">
          {{ savingPassword ? 'Saving...' : 'Change Password' }}
        </button>
      </section>
    </div>
  </section>
</template>

<style scoped>
.settings-layout {
  display: grid;
  gap: 12px;
}

.settings-head {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: linear-gradient(130deg, #fff, #f5f9ff);
  padding: 20px 22px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.settings-head h1 {
  margin: 0;
  color: var(--ink-strong);
}

.settings-head button,
.card button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 8px 14px;
  cursor: pointer;
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.success-text {
  margin: 0;
  color: var(--ok);
}

.panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 16px;
}

.settings-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.card {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 14px;
  display: grid;
  gap: 10px;
}

.card h2 {
  margin: 0;
  color: var(--ink-strong);
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  font-weight: 600;
  color: var(--ink-main);
}

.field input,
.field textarea {
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 10px 12px;
  resize: vertical;
}

.avatar-preview {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--line-soft);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-muted);
  font-size: 0.86rem;
}

@media (max-width: 920px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
