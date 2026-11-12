import { computed, onBeforeUnmount, ref, type Ref } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ApiError,
  errorMessage,
  getConfig,
  hasToken,
  saveConfig,
  type ConfigDocument,
  type ConfigKey,
  type Defaults,
  type DictionaryEntry,
  type PlatformEntry,
} from "../api";

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));
export function useConfigEditor<TValues = Defaults, TEntry = DictionaryEntry | PlatformEntry>(key: ConfigKey) {
  const document = ref(null) as Ref<ConfigDocument<TValues, TEntry> | null>;
  const original = ref("");
  const loading = ref(false);
  const saving = ref(false);
  const loadError = ref("");
  const conflict = ref(false);
  const serverVersion = ref(null) as Ref<ConfigDocument<TValues, TEntry> | null>;
  const conflictVisible = ref(false);
  const dirty = computed(() =>
    Boolean(
      document.value && JSON.stringify(document.value) !== original.value,
    ),
  );
  function accept(value: ConfigDocument<TValues, TEntry>) {
    document.value = clone(value);
    original.value = JSON.stringify(value);
    conflict.value = false;
    serverVersion.value = null;
  }
  async function load() {
    loading.value = true;
    loadError.value = "";
    try {
      accept(await getConfig<TValues, TEntry>(key));
    } catch (error) {
      loadError.value = errorMessage(error);
    } finally {
      loading.value = false;
    }
  }
  async function refresh() {
    if (dirty.value) {
      try {
        await ElMessageBox.confirm(
          "重新载入会丢弃当前尚未发布的修改，确定继续吗？",
          "重新载入配置",
          {
            type: "warning",
            confirmButtonText: "丢弃并重新载入",
            cancelButtonText: "保留草稿",
          },
        );
      } catch {
        return;
      }
    }
    await load();
  }
  async function save() {
    if (!document.value || !dirty.value || conflict.value || saving.value)
      return;
    saving.value = true;
    try {
      accept(await saveConfig(document.value));
      ElMessage.success("已发布，客户端下次读取时使用新配置。");
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        conflict.value = true;
        ElMessage.warning("服务器配置已更新，当前草稿已保留。请先核对版本。");
        await inspectConflict();
      } else ElMessage.error(errorMessage(error));
    } finally {
      saving.value = false;
    }
  }
  async function inspectConflict() {
    try {
      serverVersion.value = await getConfig<TValues, TEntry>(key);
      conflictVisible.value = true;
    } catch (error) {
      ElMessage.error(errorMessage(error));
    }
  }
  async function keepDraft() {
    if (!document.value || !serverVersion.value) return;
    try {
      await ElMessageBox.confirm(
        "草稿将保留，并以刚查看的服务器版本继续编辑。请核对他人的修改后再次保存发布；此操作不会立即发布。",
        "保留草稿继续编辑",
        {
          type: "warning",
          confirmButtonText: "继续编辑",
          cancelButtonText: "返回核对",
        },
      );
      document.value.revision = serverVersion.value.revision;
      document.value.updatedAt = serverVersion.value.updatedAt;
      original.value = JSON.stringify(serverVersion.value);
      conflict.value = false;
      conflictVisible.value = false;
    } catch {
      /* cancellation preserves both versions */
    }
  }
  async function useServer() {
    if (!serverVersion.value) return;
    try {
      await ElMessageBox.confirm(
        "将丢弃当前草稿并采用服务器版本。",
        "采用服务器版本",
        {
          type: "warning",
          confirmButtonText: "丢弃草稿",
          cancelButtonText: "保留草稿",
        },
      );
      accept(serverVersion.value);
      conflictVisible.value = false;
    } catch {
      /* keep draft */
    }
  }
  async function confirmLeave() {
    if (!dirty.value || !hasToken()) return true;
    try {
      await ElMessageBox.confirm(
        "当前修改尚未发布，离开后会丢失。",
        "未保存的配置",
        {
          type: "warning",
          confirmButtonText: "放弃并离开",
          cancelButtonText: "继续编辑",
        },
      );
      return true;
    } catch {
      return false;
    }
  }
  onBeforeRouteLeave(confirmLeave);
  onBeforeRouteUpdate(confirmLeave);
  const beforeUnload = (event: BeforeUnloadEvent) => {
    if (dirty.value) {
      event.preventDefault();
      event.returnValue = "";
    }
  };
  window.addEventListener("beforeunload", beforeUnload);
  onBeforeUnmount(() =>
    window.removeEventListener("beforeunload", beforeUnload),
  );
  return {
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
  };
}
