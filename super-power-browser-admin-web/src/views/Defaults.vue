<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { errorMessage, getConfig, type DictionaryEntry } from "../api";
import { useConfigEditor } from "../composables/useConfigEditor";
import ConfigStatus from "../components/ConfigStatus.vue";
import ConflictDialog from "../components/ConflictDialog.vue";
const {
  document,
  dirty,
  loading,
  saving,
  loadError,
  conflict,
  serverVersion,
  conflictVisible,
  load,
  refresh,
  save,
  inspectConflict,
  keepDraft,
  useServer,
} = useConfigEditor("browser-defaults");
const choices = ref<Record<string, DictionaryEntry[]>>({
  timezone: [],
  language: [],
  resolution: [],
});
const choiceError = ref("");
const fields = [
  {
    key: "timezone",
    title: "默认时区",
    hint: "新建店铺时预选的时区。",
    path: "/environment/timezones",
  },
  {
    key: "language",
    title: "默认语言",
    hint: "新建店铺时预选的浏览器语言。",
    path: "/environment/languages",
  },
  {
    key: "resolution",
    title: "默认窗口尺寸",
    hint: "新建店铺时预选的浏览器窗口尺寸。",
    path: "/environment/resolutions",
  },
] as const;
const invalid = computed(() =>
  fields.some(
    (field) =>
      document.value?.values &&
      !choices.value[field.key].some(
        (row) => row.value === document.value!.values![field.key],
      ),
  ),
);
async function loadChoices() {
  choiceError.value = "";
  try {
    const docs = await Promise.all(
      ["timezones", "languages", "resolutions"].map((key) =>
        getConfig(key as any),
      ),
    );
    fields.forEach((field, index) => {
      choices.value[field.key] = (docs[index].entries as DictionaryEntry[])
        .filter((row) => row.enabled)
        .sort((a, b) => a.sortOrder - b.sortOrder);
    });
  } catch (error) {
    choiceError.value = errorMessage(error);
  }
}
async function publish() {
  if (invalid.value || choiceError.value) {
    ElMessage.warning("请先为全部默认值选择有效的启用项。");
    return;
  }
  await save();
}
async function reload() {
  await Promise.all([refresh(), loadChoices()]);
}
onMounted(() => Promise.all([load(), loadChoices()]));
</script>
<template>
  <section class="page-container">
    <div class="page-heading">
      <div>
        <h1>环境默认值</h1>
        <p>统一新建店铺的默认选项。已创建店铺的设置不会随默认值更新。</p>
      </div>
    </div>
    <el-alert
      v-if="loadError || choiceError"
      :title="loadError || choiceError"
      type="error"
      :closable="false"
      show-icon
      class="section-alert"
      ><template #default
        ><el-button link type="primary" @click="reload"
          >重试加载</el-button
        ></template
      ></el-alert
    >
    <el-card shadow="never" v-loading="loading"
      ><ConfigStatus
        :revision="document?.revision"
        :updated-at="document?.updatedAt"
        :dirty="dirty"
        :saving="saving"
        :loading="loading"
        :conflict="conflict"
        @save="publish"
        @refresh="reload"
        @inspect="inspectConflict"
      />
      <el-alert
        v-if="invalid && !choiceError"
        title="部分默认值已不在启用选项中，请重新选择后发布。"
        type="warning"
        :closable="false"
        show-icon
        class="section-alert"
      />
      <el-form
        v-if="document?.values"
        class="defaults-form"
        label-position="top"
        :disabled="saving"
        ><el-form-item
          v-for="field in fields"
          :key="field.key"
          :label="field.title"
          required
          ><el-select
            v-model="document.values[field.key]"
            filterable
            :placeholder="`请选择${field.title}`"
            ><el-option
              v-if="
                !choices[field.key].some(
                  (row) => row.value === document!.values![field.key],
                )
              "
              :value="document.values[field.key]"
              :label="`${document.values[field.key]}（当前值，已不可选）`"
              disabled /><el-option
              v-for="row in choices[field.key]"
              :key="row.value"
              :label="`${row.label} · ${row.value}`"
              :value="row.value"
          /></el-select>
          <div class="field-help">
            {{ field.hint }}
            <router-link :to="field.path">管理可选项</router-link>
          </div></el-form-item
        ></el-form
      > </el-card
    ><ConflictDialog
      v-model="conflictVisible"
      :server="serverVersion"
      :draft="document"
      @keep="keepDraft"
      @use-server="useServer"
    />
  </section>
</template>
