<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name" :title="appInfo?.appName || '网站生成器'">
          {{ appInfo?.appName || '网站生成器' }}
        </h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
        <a-popover v-if="appInfo?.versionNumber" title="版本历史" trigger="click">
          <template #content>
            <div v-for="version in versions" :key="version.id" class="version-history-item">
              <span>v{{ version.versionNumber }}</span>
              <a-tag :color="version.status === 'READY' ? 'success' : 'default'">{{ version.status }}</a-tag>
              <a-button
                v-if="version.status === 'READY' && version.versionNumber !== appInfo.versionNumber"
                type="link"
                size="small"
                :loading="rollingBackVersionId === version.id"
                @click.stop="rollbackToVersion(version)"
              >
                回滚
              </a-button>
              <a-button
                v-if="version.status === 'READY'"
                type="link"
                size="small"
                :loading="diffLoading"
                @click.stop="showVersionDiff(version)"
              >
                变更
              </a-button>
              <a-tag v-else-if="version.versionNumber === appInfo.versionNumber" color="blue">当前</a-tag>
            </div>
            <a-button block size="small" @click="openVersionCompare">任意版本对比</a-button>
          </template>
          <a-tag color="geekblue">v{{ appInfo.versionNumber }}</a-tag>
        </a-popover>
        <a-tag v-if="appInfo?.generationStatus === 'GENERATING'" color="processing">生成中</a-tag>
        <a-tag v-else-if="appInfo?.generationStatus === 'FAILED'" color="error">生成失败</a-tag>
        <a-tag v-else-if="appInfo?.generationStatus === 'CANCELLED'" color="warning">已停止</a-tag>
        <a-tag v-if="appInfo?.deployStatus === 'PAUSED'" color="warning">已暂停</a-tag>
      </div>
      <div class="header-right">
        <a-tooltip :title="appInfo?.ragEnabled ? 'RAG 会检索这个项目的历史需求与代码' : '开启 RAG'">
          <div v-if="isOwner" class="rag-toggle">
            <span>RAG</span>
            <a-switch
              size="small"
              :checked="Boolean(appInfo?.ragEnabled)"
              :loading="ragToggleLoading"
              :disabled="isGenerating"
              @change="toggleRagMemory"
            />
          </div>
        </a-tooltip>
        <a-button
            class="header-action-button"
            type="default"
            aria-label="应用详情"
            title="应用详情"
            @click="showAppDetail"
        >
          <template #icon>
            <InfoCircleOutlined />
          </template>
          <span class="header-action-label">应用详情</span>
        </a-button>
        <a-button
            class="header-action-button"
            type="primary"
            ghost
            aria-label="下载代码"
            title="下载代码"
            @click="downloadCode"
            :loading="downloading"
            :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          <span class="header-action-label">下载代码</span>
        </a-button>
        <a-button
            class="header-action-button"
            type="primary"
            aria-label="部署"
            title="部署"
            @click="deployApp"
            :loading="deploying"
        >
          <template #icon>
            <CloudUploadOutlined />
          </template>
          <span class="header-action-label">部署</span>
        </a-button>
        <a-button
            v-if="appInfo?.deployKey && appInfo.deployStatus === 'RUNNING'"
            class="header-action-button"
            aria-label="暂停部署"
            title="暂停部署"
            @click="pauseDeployment"
        >
          <template #icon><PauseCircleOutlined /></template>
          <span class="header-action-label">暂停</span>
        </a-button>
        <a-button
            v-else-if="appInfo?.deployKey"
            class="header-action-button"
            aria-label="恢复部署"
            title="恢复部署"
            @click="resumeDeployment"
        >
          <template #icon><PlayCircleOutlined /></template>
          <span class="header-action-label">恢复</span>
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div ref="mainContent" class="main-content" :style="mainContentStyle">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>{{ message.statusText || 'AI 正在思考...' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
            v-if="selectedElementInfo"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div v-if="isGenerating && latestGenerationTask" class="persistent-task-status">
            <div class="task-status-heading">
              <span>{{ latestGenerationTask.currentStep || '正在生成' }}</span>
              <span>{{ latestGenerationTask.progress || 0 }}%</span>
            </div>
            <a-progress
                :percent="latestGenerationTask.progress || 0"
                :show-info="false"
                size="small"
                status="active"
            />
            <div v-if="latestGenerationTask.currentFile" class="task-current-file">
              {{ latestGenerationTask.currentFile }}
            </div>
          </div>
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                  v-model:value="userInput"
                  :placeholder="getInputPlaceholder()"
                  :rows="4"
                  :maxlength="1000"
                  @keydown.enter.prevent="sendMessage"
                  :disabled="isGenerating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
                v-else
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating || planningGeneration"
            />
            <div class="input-actions">
              <a-button
                  v-if="canResumeGeneration"
                  type="primary"
                  ghost
                  @click="resumeCodeGeneration"
              >
                <template #icon>
                  <PlayCircleOutlined />
                </template>
                恢复生成
              </a-button>
              <a-button
                  v-else-if="canRegenerateMissingProject"
                  type="primary"
                  ghost
                  @click="regenerateMissingProject"
              >
                <template #icon>
                  <ReloadOutlined />
                </template>
                重新生成项目
              </a-button>
              <a-button
                  v-if="isGenerating"
                  danger
                  @click="cancelGeneration"
                  title="停止生成"
              >
                停止
              </a-button>
              <a-button
                  type="primary"
                  @click="sendMessage"
                  :loading="isGenerating || planningGeneration"
                  :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div
          class="resize-handle main-resize-handle"
          role="separator"
          aria-label="调整对话区和工作区宽度"
          aria-orientation="vertical"
          tabindex="0"
          @pointerdown="startMainResize"
          @keydown.left.prevent="adjustMainResize(-0.02)"
          @keydown.right.prevent="adjustMainResize(0.02)"
      >
        <span class="resize-handle-grip"></span>
      </div>
      <div class="preview-section">
        <div class="preview-header">
          <div class="workspace-heading">
            <h3>{{ workspaceMode === 'preview' ? '网站预览' : workspaceMode === 'code' ? '项目文件' : '数据管理' }}</h3>
            <span v-if="workspaceMode === 'code' && generatedFiles.length">
              {{ generatedFiles.length }} 个文件
            </span>
            <span
                v-else-if="workspaceMode === 'preview' && buildResult"
                class="build-status-label"
                :class="buildResult.success ? 'success' : 'error'"
            >
              {{ buildResult.success ? '构建通过' : '构建失败' }}
            </span>
          </div>
          <div class="workspace-switch" role="tablist" aria-label="工作区视图">
            <a-tooltip title="网站预览">
              <button
                  type="button"
                  class="workspace-switch-button"
                  :class="{ active: workspaceMode === 'preview' }"
                  role="tab"
                  :aria-selected="workspaceMode === 'preview'"
                  aria-label="切换到网站预览"
                  @click="switchWorkspace('preview')"
              >
                <DesktopOutlined />
              </button>
            </a-tooltip>
            <a-tooltip title="项目文件">
              <button
                  type="button"
                  class="workspace-switch-button"
                  :class="{ active: workspaceMode === 'code' }"
                  role="tab"
                  :aria-selected="workspaceMode === 'code'"
                  aria-label="切换到项目文件"
                  @click="switchWorkspace('code')"
              >
                <CodeOutlined />
              </button>
            </a-tooltip>
            <a-tooltip v-if="supportsRuntimeData" title="数据管理">
              <button
                  type="button"
                  class="workspace-switch-button"
                  :class="{ active: workspaceMode === 'data' }"
                  role="tab"
                  :aria-selected="workspaceMode === 'data'"
                  aria-label="切换到数据管理"
                  @click="switchWorkspace('data')"
              >
                <DatabaseOutlined />
              </button>
            </a-tooltip>
          </div>
          <div v-if="workspaceMode === 'preview'" class="preview-actions">
            <div v-if="previewUrl" class="preview-device-switch" role="group" aria-label="预览设备尺寸">
              <a-tooltip title="桌面">
                <button
                    type="button"
                    :class="{ active: previewDevice === 'desktop' }"
                    aria-label="桌面预览"
                    @click="previewDevice = 'desktop'"
                ><DesktopOutlined /></button>
              </a-tooltip>
              <a-tooltip title="平板">
                <button
                    type="button"
                    :class="{ active: previewDevice === 'tablet' }"
                    aria-label="平板预览"
                    @click="previewDevice = 'tablet'"
                ><TabletOutlined /></button>
              </a-tooltip>
              <a-tooltip title="手机">
                <button
                    type="button"
                    :class="{ active: previewDevice === 'mobile' }"
                    aria-label="手机预览"
                    @click="previewDevice = 'mobile'"
                ><MobileOutlined /></button>
              </a-tooltip>
            </div>
            <a-tooltip title="刷新预览">
              <a-button v-if="previewUrl" size="small" aria-label="刷新预览" @click="refreshPreview">
                <template #icon><ReloadOutlined /></template>
              </a-button>
            </a-tooltip>
            <a-tooltip title="预览调试">
              <a-badge :count="previewErrorCount" size="small">
                <a-button
                    v-if="previewUrl"
                    size="small"
                    aria-label="预览调试"
                    :type="debugPanelOpen ? 'primary' : 'default'"
                    @click="debugPanelOpen = !debugPanelOpen"
                >
                  <template #icon><BugOutlined /></template>
                </a-button>
              </a-badge>
            </a-tooltip>
            <a-tooltip title="构建检查">
              <a-button
                  v-if="isOwner && generatedFiles.length"
                  size="small"
                  aria-label="构建检查"
                  :loading="buildChecking"
                  @click="runBuildCheck()"
              >
                <template #icon><BuildOutlined /></template>
              </a-button>
            </a-tooltip>
            <a-tooltip :title="isEditMode ? '退出编辑模式' : '可视化编辑'">
              <a-button
                  v-if="isOwner && previewUrl"
                  size="small"
                  aria-label="可视化编辑"
                  :danger="isEditMode"
                  @click="toggleEditMode"
                  :class="{ 'edit-mode-active': isEditMode }"
              >
                <template #icon><EditOutlined /></template>
              </a-button>
            </a-tooltip>
            <a-tooltip title="新窗口打开">
              <a-button v-if="previewUrl" size="small" aria-label="新窗口打开" @click="openInNewTab">
                <template #icon><ExportOutlined /></template>
              </a-button>
            </a-tooltip>
          </div>
          <div v-else-if="workspaceMode === 'code'" class="preview-actions workspace-file-actions">
            <a-button
                size="small"
                type="primary"
                :loading="savingCode"
                :disabled="!codeDirty || !isOwner || activeFile?.streaming"
                @click="saveEditedCode"
            >
              <template #icon><SaveOutlined /></template>
              保存
            </a-button>
          </div>
          <div v-else class="preview-actions"></div>
        </div>
        <div v-if="workspaceMode === 'code'" class="code-workspace" :style="codeWorkspaceStyle">
          <aside class="file-explorer">
            <div class="explorer-mode-switch" role="tablist" aria-label="项目文件工具">
              <button
                  type="button"
                  :class="{ active: explorerMode === 'files' }"
                  role="tab"
                  :aria-selected="explorerMode === 'files'"
                  @click="showFileExplorer"
              >
                <FolderOutlined />
                文件
              </button>
              <button
                  type="button"
                  :class="{ active: explorerMode === 'search' }"
                  role="tab"
                  :aria-selected="explorerMode === 'search'"
                  @click="activateFileSearch"
              >
                <SearchOutlined />
                搜索
              </button>
            </div>
            <a-input
                v-if="explorerMode === 'search'"
                ref="fileSearchInput"
                v-model:value="fileSearch"
                class="file-search"
                size="small"
                allow-clear
                placeholder="搜索文件"
            >
              <template #prefix><SearchOutlined /></template>
            </a-input>
            <div class="file-tree-scroll">
              <a-directory-tree
                  v-if="filteredFileTree.length"
                  v-model:expandedKeys="expandedTreeKeys"
                  :selected-keys="activeFilePath ? [activeFilePath] : []"
                  :tree-data="filteredFileTree"
                  :auto-expand-parent="false"
                  :default-expand-all="false"
                  :show-icon="false"
                  :block-node="true"
                  @select="handleTreeSelect"
              >
                <template #title="{ title, isLeaf, iconText, fileKind, streaming }">
                  <span class="tree-node-title" :title="title">
                    <span v-if="isLeaf" class="tree-file-icon" :class="`kind-${fileKind}`">
                      {{ iconText }}
                    </span>
                    <FolderOutlined v-else class="tree-folder-icon" />
                    <span class="tree-node-label">{{ title }}</span>
                    <span v-if="streaming" class="file-stream-dot"></span>
                  </span>
                </template>
              </a-directory-tree>
              <div v-else class="file-tree-empty">
                <a-spin v-if="isGenerating" size="small" />
                <FolderOpenOutlined v-else />
                <span>{{ isGenerating ? '等待文件输出...' : '暂无项目文件' }}</span>
              </div>
            </div>
          </aside>

          <div
              class="resize-handle explorer-resize-handle"
              role="separator"
              aria-label="调整项目目录和文件内容宽度"
              aria-orientation="vertical"
              tabindex="0"
              @pointerdown="startExplorerResize"
              @keydown.left.prevent="adjustExplorerResize(-16)"
              @keydown.right.prevent="adjustExplorerResize(16)"
          >
            <span class="resize-handle-grip"></span>
          </div>

          <section class="file-viewer">
            <template v-if="activeFile">
              <div class="code-editor-toolbar">
                <div class="code-file-meta">
                  <FileTextOutlined />
                  <span class="code-file-path">{{ activeFile.path }}</span>
                  <span class="language-label">{{ activeFile.language || 'text' }}</span>
                </div>
                <span v-if="activeFile.streaming" class="code-streaming">
                  <a-spin size="small" />
                  流式生成中
                </span>
                <span v-else-if="codeDirty" class="code-dirty">未保存</span>
              </div>
              <MonacoCodeEditor
                  class="code-editor"
                  v-model="editingCode"
                  :path="activeFile.path"
                  :language="activeFile.language || 'text'"
                  :read-only="!isOwner || Boolean(activeFile.streaming)"
                  @change="onCodeChange"
                  @save="saveEditedCode"
              />
            </template>
            <div v-else class="file-viewer-empty">
              <FileTextOutlined />
              <p>{{ generatedFiles.length ? '从左侧选择文件查看' : '生成的文件将在这里实时显示' }}</p>
            </div>
          </section>
        </div>
        <RuntimeDataPanel v-else-if="workspaceMode === 'data'" :app-id="String(appId)" />
        <div v-else class="preview-content" :class="{ 'debug-panel-open': debugPanelOpen }">
          <div class="preview-stage">
            <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
              <div class="placeholder-icon">🌐</div>
              <p>网站文件生成完成后将在这里展示</p>
            </div>
            <div v-else-if="isGenerating" class="preview-loading">
              <a-spin size="large" />
              <p>正在生成网站...</p>
            </div>
            <div v-else class="preview-device-frame" :class="`device-${previewDevice}`">
              <iframe
                  ref="previewIframe"
                  :src="iframePreviewUrl"
                  class="preview-iframe"
                  sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
                  frameborder="0"
                  @load="onIframeLoad"
              ></iframe>
            </div>
          </div>
          <section v-if="debugPanelOpen" class="preview-debug-panel">
            <div class="debug-panel-tabs" role="tablist" aria-label="预览调试信息">
              <button
                  v-for="tab in debugTabs"
                  :key="tab.key"
                  type="button"
                  :class="{ active: activeDebugTab === tab.key }"
                  @click="activeDebugTab = tab.key"
              >
                {{ tab.label }}<span v-if="tab.count">{{ tab.count }}</span>
              </button>
              <a-tooltip title="清空日志">
                <a-button
                    v-if="activeDebugTab !== 'build'"
                    type="text"
                    size="small"
                    aria-label="清空调试日志"
                    @click="clearPreviewLogs"
                ><template #icon><ClearOutlined /></template></a-button>
              </a-tooltip>
            </div>
            <div v-if="activeDebugTab === 'build'" class="build-panel-content">
              <div class="build-panel-summary">
                <div>
                  <strong>{{ buildStatusTitle }}</strong>
                  <span v-if="buildResult">{{ formatDuration(buildResult.durationMs) }}</span>
                </div>
                <div class="build-panel-actions">
                  <a-button size="small" :loading="buildChecking" @click="runBuildCheck()">
                    <template #icon><BuildOutlined /></template>
                    重新检查
                  </a-button>
                  <a-button
                      v-if="buildResult && !buildResult.success && isOwner && canAutoRepairBuild"
                      size="small"
                      type="primary"
                      :disabled="isGenerating"
                      @click="startBuildRepair"
                  >
                    <template #icon><ToolOutlined /></template>
                    AI 自动修复
                  </a-button>
                  <a-button
                      v-else-if="buildResult && !buildResult.success && isOwner && buildResult.stage === 'PROJECT'"
                      size="small"
                      type="primary"
                      :disabled="isGenerating"
                      @click="regenerateMissingProject"
                  >
                    <template #icon><ReloadOutlined /></template>
                    重新生成项目
                  </a-button>
                </div>
              </div>
              <div v-if="repairRounds.length" class="repair-rounds">
                <span
                    v-for="round in repairRounds"
                    :key="round.attempt"
                    :class="round.success ? 'success' : 'failed'"
                >
                  第 {{ round.attempt }} 轮 · {{ round.success ? '通过' : '未通过' }}
                </span>
              </div>
              <pre>{{ buildResult?.output || '点击“重新检查”运行项目构建。' }}</pre>
            </div>
            <div v-else-if="activePreviewLogs.length" class="debug-log-list">
              <button
                  v-for="entry in activePreviewLogs"
                  :key="entry.id"
                  type="button"
                  class="debug-log-entry"
                  :class="`level-${entry.level}`"
                  @click="openDebugEntryFile(entry)"
              >
                <span class="debug-log-time">{{ entry.time }}</span>
                <span class="debug-log-level">{{ entry.level }}</span>
                <span class="debug-log-message">{{ entry.message }}</span>
              </button>
            </div>
            <a-empty v-else :description="activeDebugTab === 'console' ? '暂无 Console 日志' : '暂无网络请求记录'" />
          </section>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
        v-model:open="appDetailVisible"
        :app="appInfo"
        :show-actions="isOwner || isAdmin"
        @edit="editApp"
        @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
        v-model:open="deployModalVisible"
        :deploy-url="deployUrl"
        @open-site="openDeployedSite"
    />

    <a-modal v-model:open="diffVisible" title="代码 Diff" width="1180px" :footer="null">
      <div class="diff-toolbar">
        <a-select v-model:value="diffBaseVersionId" style="width: 180px" @change="loadVersionDiff">
          <a-select-option v-for="version in readyVersions" :key="version.id" :value="String(version.id)">
            v{{ version.versionNumber }} · {{ version.changeMessage || '版本' }}
          </a-select-option>
        </a-select>
        <span>对比</span>
        <a-select v-model:value="diffTargetVersionId" style="width: 180px" @change="loadVersionDiff">
          <a-select-option value="current">当前工作区</a-select-option>
          <a-select-option v-for="version in readyVersions" :key="version.id" :value="String(version.id)">
            v{{ version.versionNumber }} · {{ version.changeMessage || '版本' }}
          </a-select-option>
        </a-select>
        <a-segmented v-model:value="diffViewMode" :options="[{ label: '并排', value: 'split' }, { label: '统一', value: 'unified' }]" />
        <a-spin v-if="diffLoading" size="small" />
      </div>
      <a-empty v-if="!diffLoading && !versionDiffs.length" description="两个版本没有文件差异" />
      <div v-else-if="activeVersionDiff" class="version-diff-workspace">
        <aside class="diff-file-list">
          <button
              v-for="diff in versionDiffs"
              :key="diff.path"
              type="button"
              :class="{ active: diff.path === activeDiffPath }"
              @click="activeDiffPath = diff.path"
          >
            <span>{{ diff.path }}</span>
            <small>+{{ diff.additions || 0 }} -{{ diff.deletions || 0 }}</small>
          </button>
        </aside>
        <section class="diff-file-viewer">
          <div class="version-diff-heading">
            <span class="version-diff-path">{{ activeVersionDiff.path }}</span>
            <a-tag :color="activeVersionDiff.status === 'ADDED' ? 'green' : activeVersionDiff.status === 'DELETED' ? 'red' : 'blue'">
              {{ activeVersionDiff.status }}
            </a-tag>
            <span class="version-diff-count">+{{ activeVersionDiff.additions || 0 }} / -{{ activeVersionDiff.deletions || 0 }}</span>
            <a-button
                v-if="diffTargetVersionId === 'current' && isOwner"
                size="small"
                :loading="restoringDiffFile"
                @click="restoreActiveDiffFile"
            >恢复此文件到基准版本</a-button>
          </div>
          <div v-if="diffViewMode === 'split'" class="line-diff split-diff">
            <div v-for="(line, index) in activeVersionDiff.lines" :key="index" class="split-diff-row" :class="`diff-${line.type.toLowerCase()}`">
              <template v-if="line.type === 'HUNK'">
                <div class="diff-hunk">···</div><div class="diff-hunk">···</div>
              </template>
              <template v-else>
                <div class="diff-line before"><span>{{ line.beforeLineNumber || '' }}</span><code>{{ line.beforeContent ?? '' }}</code></div>
                <div class="diff-line after"><span>{{ line.afterLineNumber || '' }}</span><code>{{ line.afterContent ?? '' }}</code></div>
              </template>
            </div>
          </div>
          <div v-else class="line-diff unified-diff">
            <template v-for="(line, index) in activeVersionDiff.lines" :key="index">
              <div v-if="line.type === 'HUNK'" class="diff-hunk">···</div>
              <div v-else-if="line.type === 'MODIFIED'" class="diff-line diff-deleted"><span>{{ line.beforeLineNumber }}</span><b>-</b><code>{{ line.beforeContent }}</code></div>
              <div v-if="line.type === 'MODIFIED'" class="diff-line diff-added"><span>{{ line.afterLineNumber }}</span><b>+</b><code>{{ line.afterContent }}</code></div>
              <div v-else-if="line.type !== 'HUNK'" class="diff-line" :class="`diff-${line.type.toLowerCase()}`">
                <span>{{ line.type === 'ADDED' ? line.afterLineNumber : line.beforeLineNumber }}</span>
                <b>{{ line.type === 'ADDED' ? '+' : line.type === 'DELETED' ? '-' : ' ' }}</b>
                <code>{{ line.type === 'ADDED' ? line.afterContent : line.beforeContent }}</code>
              </div>
            </template>
          </div>
        </section>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed, watch, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  listAppVersions,
  rollbackAppVersion,
  diffAppVersion,
  restoreAppVersionFile,
  createGenerationPlan,
  startCodeGeneration,
  getLatestGenerationTask,
  checkAppBuild,
  saveAppCodeFile,
  listAppCodeFiles,
  deployApp as deployAppApi,
  startDeployment,
  stopDeployment,
  deleteApp as deleteAppApi,
  toggleAppRag,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import RuntimeDataPanel from '@/components/RuntimeDataPanel.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  DesktopOutlined,
  CodeOutlined,
  SaveOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  SearchOutlined,
  FileTextOutlined,
  TabletOutlined,
  MobileOutlined,
  ReloadOutlined,
  BugOutlined,
  BuildOutlined,
  ClearOutlined,
  ToolOutlined,
  DatabaseOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const MonacoCodeEditor = defineAsyncComponent(() => import('@/components/MonacoCodeEditor.vue'))

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<any>()
const versions = ref<any[]>([])
const rollingBackVersionId = ref<string | null>(null)
const diffVisible = ref(false)
interface CodeDiffLine {
  type: 'CONTEXT' | 'MODIFIED' | 'ADDED' | 'DELETED' | 'HUNK'
  beforeLineNumber?: number
  afterLineNumber?: number
  beforeContent?: string
  afterContent?: string
}

interface CodeFileDiff {
  path: string
  status: 'ADDED' | 'DELETED' | 'MODIFIED'
  additions: number
  deletions: number
  unifiedDiff?: string
  lines: CodeDiffLine[]
}

const versionDiffs = ref<CodeFileDiff[]>([])
const diffLoading = ref(false)
const diffBaseVersionId = ref('')
const diffTargetVersionId = ref('current')
const diffViewMode = ref<'split' | 'unified'>('split')
const activeDiffPath = ref('')
const restoringDiffFile = ref(false)
let activeEventSource: EventSource | null = null

const readyVersions = computed(() => versions.value.filter((version) => version.status === 'READY'))
const supportsRuntimeData = computed(() => appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT)
const activeVersionDiff = computed(() => versionDiffs.value.find((diff) => diff.path === activeDiffPath.value)
  || versionDiffs.value[0])

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  statusText?: string
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)
const previewIframe = ref<HTMLIFrameElement>()
const previewDevice = ref<'desktop' | 'tablet' | 'mobile'>('desktop')
const debugPanelOpen = ref(false)
const activeDebugTab = ref<'console' | 'network' | 'build'>('console')
const buildChecking = ref(false)

interface BuildCheckResult {
  success: boolean
  stage: string
  output: string
  durationMs: number
  checkedAt?: string
}

interface GenerationTaskState {
  id: string
  status: 'GENERATING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'PAUSED'
  currentStep?: string
  currentFile?: string
  progress?: number
  errorMessage?: string
  taskType?: 'GENERATION' | 'AUTO_REPAIR'
  attempt?: number
  maxAttempts?: number
  buildStatus?: 'PASSED' | 'FAILED'
  buildOutput?: string
  updateTime?: string
}

interface PreviewDebugEntry {
  id: number
  kind: 'console' | 'network'
  level: 'log' | 'info' | 'warn' | 'error'
  message: string
  time: string
  file?: string
}

interface RepairRound {
  attempt: number
  success: boolean
  stage: string
  durationMs: number
}

const buildResult = ref<BuildCheckResult | null>(null)
const repairRounds = ref<RepairRound[]>([])
const repairActive = ref(false)
const repairAttempt = ref(0)
const maxRepairAttempts = 3
const latestGenerationTask = ref<GenerationTaskState | null>(null)
const previewLogs = ref<PreviewDebugEntry[]>([])
let previewLogSequence = 0
let taskPollTimer: ReturnType<typeof setInterval> | null = null
let taskRecoveryStarted = false
let repairCompletionHandling = false

const iframePreviewUrl = computed(() => {
  if (!previewUrl.value) return ''
  const separator = previewUrl.value.includes('?') ? '&' : '?'
  return `${previewUrl.value}${separator}debug=1`
})

const consoleLogs = computed(() => previewLogs.value.filter((entry) => entry.kind === 'console'))
const networkLogs = computed(() => previewLogs.value.filter((entry) => entry.kind === 'network'))
const activePreviewLogs = computed(() => activeDebugTab.value === 'console' ? consoleLogs.value : networkLogs.value)
const previewErrorCount = computed(() => previewLogs.value.filter((entry) => entry.level === 'error').length)
const debugTabs = computed(() => [
  { key: 'console' as const, label: 'Console', count: consoleLogs.value.length },
  { key: 'network' as const, label: '网络', count: networkLogs.value.length },
  { key: 'build' as const, label: '构建', count: buildResult.value && !buildResult.value.success ? 1 : 0 },
])
const buildStatusTitle = computed(() => {
  if (buildChecking.value) return '正在执行构建检查'
  if (!buildResult.value) return '尚未运行构建检查'
  return buildResult.value.success ? '构建检查通过' : `构建失败 · ${buildResult.value.stage}`
})
const canAutoRepairBuild = computed(() => {
  return !!buildResult.value && ['DEPENDENCIES', 'BUILD', 'OUTPUT'].includes(buildResult.value.stage)
})

interface GeneratedFile {
  path: string
  content: string
  language: string
  operation?: string
  summary?: string
  streamId?: string
  streaming?: boolean
}

interface GeneratedFileDelta {
  type: 'code_file_delta'
  streamId?: string
  path: string
  delta?: string
  language?: string
  operation?: string
  reset?: boolean
}

interface GenerationPlan {
  title: string
  summary: string
  pages: string[]
  features: string[]
  techStack: string[]
  needsImages: boolean
  needsDatabase: boolean
  dataModels: GenerationDataModel[]
}

type PlanFieldType = 'STRING' | 'TEXT' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME' | 'ENUM'
type PlanOperation = 'READ' | 'CREATE' | 'UPDATE' | 'DELETE'
interface GenerationDataField {
  key: string
  name: string
  type: PlanFieldType
  required: boolean
  maxLength?: number
  options?: string[]
}
interface GenerationDataModel {
  modelKey: string
  displayName: string
  fields: GenerationDataField[]
  publicOperations: PlanOperation[]
}

const planFieldTypes = new Set<PlanFieldType>([
  'STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'ENUM',
])

const generatedFiles = ref<GeneratedFile[]>([])
const activeFilePath = ref('')
const editingCode = ref('')
const codeDirty = ref(false)
const savingCode = ref(false)
const workspaceMode = ref<'preview' | 'code' | 'data'>('preview')
const explorerMode = ref<'files' | 'search'>('files')
const fileSearch = ref('')
const fileSearchInput = ref<any>()
const expandedTreeKeys = ref<Array<string | number>>([])
const expandedBeforeSearch = ref<Array<string | number>>([])
const planningGeneration = ref(false)
const generationPlan = ref<GenerationPlan | null>(null)
const mainContent = ref<HTMLElement>()
const chatWidthPercent = ref(40)
const explorerWidth = ref(238)

const mainContentStyle = computed(() => ({
  '--chat-width': `${chatWidthPercent.value}%`,
}))

const codeWorkspaceStyle = computed(() => ({
  '--explorer-width': `${explorerWidth.value}px`,
}))

type ResizeTarget = 'main' | 'explorer'
let activeResizeTarget: ResizeTarget | null = null
let resizeStartX = 0
let resizeStartChatWidth = 0
let resizeStartExplorerWidth = 0

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max)

const startMainResize = (event: PointerEvent) => {
  if (window.matchMedia('(max-width: 1024px)').matches || !mainContent.value) return
  activeResizeTarget = 'main'
  resizeStartX = event.clientX
  resizeStartChatWidth = chatWidthPercent.value
  document.body.classList.add('is-resizing-columns')
  window.addEventListener('pointermove', handleResize)
  window.addEventListener('pointerup', stopResize, { once: true })
  event.preventDefault()
}

const startExplorerResize = (event: PointerEvent) => {
  if (window.matchMedia('(max-width: 1024px)').matches) return
  activeResizeTarget = 'explorer'
  resizeStartX = event.clientX
  resizeStartExplorerWidth = explorerWidth.value
  document.body.classList.add('is-resizing-columns')
  window.addEventListener('pointermove', handleResize)
  window.addEventListener('pointerup', stopResize, { once: true })
  event.preventDefault()
}

const handleResize = (event: PointerEvent) => {
  if (!activeResizeTarget) return
  const delta = event.clientX - resizeStartX
  if (activeResizeTarget === 'main' && mainContent.value) {
    const containerWidth = mainContent.value.clientWidth
    const minChatWidth = 280
    const minWorkspaceWidth = 420
    const availableWidth = Math.max(containerWidth - 16, minChatWidth + minWorkspaceWidth)
    const nextWidth = ((resizeStartChatWidth / 100) * availableWidth + delta) / availableWidth * 100
    chatWidthPercent.value = clamp(nextWidth, (minChatWidth / availableWidth) * 100, 100 - (minWorkspaceWidth / availableWidth) * 100)
  } else if (activeResizeTarget === 'explorer') {
    explorerWidth.value = clamp(resizeStartExplorerWidth + delta, 180, 420)
  }
}

const stopResize = () => {
  activeResizeTarget = null
  document.body.classList.remove('is-resizing-columns')
  window.removeEventListener('pointermove', handleResize)
}

const adjustMainResize = (delta: number) => {
  if (window.matchMedia('(max-width: 1024px)').matches) return
  chatWidthPercent.value = clamp(chatWidthPercent.value + delta, 25, 65)
}

const adjustExplorerResize = (delta: number) => {
  if (window.matchMedia('(max-width: 1024px)').matches) return
  explorerWidth.value = clamp(explorerWidth.value + delta, 180, 420)
}

interface FileTreeNode {
  title: string
  key: string
  isLeaf: boolean
  selectable?: boolean
  streaming?: boolean
  fileKind?: string
  iconText?: string
  children?: FileTreeNode[]
}

const getFileVisual = (path: string) => {
  const name = path.split('/').pop()?.toLowerCase() || ''
  const extension = name.includes('.') ? name.split('.').pop() || '' : ''
  if (extension === 'vue') return { fileKind: 'vue', iconText: 'V' }
  if (extension === 'ts' || extension === 'tsx') return { fileKind: 'typescript', iconText: 'TS' }
  if (extension === 'js' || extension === 'jsx' || extension === 'mjs') return { fileKind: 'javascript', iconText: 'JS' }
  if (extension === 'html' || extension === 'htm') return { fileKind: 'html', iconText: '<>' }
  if (extension === 'css' || extension === 'scss' || extension === 'less') return { fileKind: 'style', iconText: '#' }
  if (extension === 'json') return { fileKind: 'json', iconText: '{}' }
  if (extension === 'md') return { fileKind: 'markdown', iconText: 'i' }
  if (extension === 'yaml' || extension === 'yml') return { fileKind: 'yaml', iconText: 'Y' }
  if (name.endsWith('lock')) return { fileKind: 'lock', iconText: '~' }
  return { fileKind: 'text', iconText: '=' }
}

const buildFileTree = (files: GeneratedFile[]): FileTreeNode[] => {
  const roots: FileTreeNode[] = []
  const folders = new Map<string, FileTreeNode>()

  files
    .slice()
    .sort((left, right) => left.path.localeCompare(right.path))
    .forEach((file) => {
      const parts = file.path.replace(/\\/g, '/').split('/').filter(Boolean)
      let children = roots
      let folderPath = ''

      parts.forEach((part, index) => {
        const isFile = index === parts.length - 1
        if (isFile) {
          const visual = getFileVisual(file.path)
          children.push({
            title: part,
            key: file.path,
            isLeaf: true,
            streaming: file.streaming,
            ...visual,
          })
          return
        }

        folderPath = folderPath ? `${folderPath}/${part}` : part
        const folderKey = `folder:${folderPath}`
        let folder = folders.get(folderKey)
        if (!folder) {
          folder = {
            title: part,
            key: folderKey,
            isLeaf: false,
            selectable: false,
            children: [],
          }
          folders.set(folderKey, folder)
          children.push(folder)
        }
        children = folder.children!
      })
    })

  const sortNodes = (nodes: FileTreeNode[]) => {
    nodes.sort((left, right) => {
      if (left.isLeaf !== right.isLeaf) return left.isLeaf ? 1 : -1
      return left.title.localeCompare(right.title)
    })
    nodes.forEach((node) => node.children && sortNodes(node.children))
  }
  sortNodes(roots)
  return roots
}

const fileTree = computed(() => buildFileTree(generatedFiles.value))
const filteredFileTree = computed(() => {
  const query = fileSearch.value.trim().toLowerCase()
  if (!query) return fileTree.value
  return buildFileTree(generatedFiles.value.filter((file) => file.path.toLowerCase().includes(query)))
})

const collectFolderKeys = (nodes: FileTreeNode[], result: string[] = []) => {
  nodes.forEach((node) => {
    if (!node.isLeaf) result.push(node.key)
    if (node.children) collectFolderKeys(node.children, result)
  })
  return result
}

const activeFile = computed(() => generatedFiles.value.find(file => file.path === activeFilePath.value))

const selectGeneratedFile = (file: GeneratedFile) => {
  activeFilePath.value = file.path
  editingCode.value = file.content
  codeDirty.value = false
}

const openGeneratedFile = (file: GeneratedFile) => {
  workspaceMode.value = 'code'
  if (file.path === activeFilePath.value) return
  selectGeneratedFile(file)
}

const switchWorkspace = (mode: 'preview' | 'code' | 'data') => {
  workspaceMode.value = mode
  if (mode === 'code' && !activeFile.value && generatedFiles.value.length) {
    selectGeneratedFile(generatedFiles.value[0])
  }
}

const activateFileSearch = () => {
  if (explorerMode.value !== 'search') {
    expandedBeforeSearch.value = [...expandedTreeKeys.value]
  }
  explorerMode.value = 'search'
  nextTick(() => fileSearchInput.value?.focus?.())
}

const showFileExplorer = () => {
  explorerMode.value = 'files'
  fileSearch.value = ''
  expandedTreeKeys.value = [...expandedBeforeSearch.value]
}

const handleTreeSelect = (keys: Array<string | number>) => {
  const selectedPath = keys[0] == null ? '' : String(keys[0])
  const file = generatedFiles.value.find((item) => item.path === selectedPath)
  if (file) openGeneratedFile(file)
}

const upsertGeneratedFile = (file: GeneratedFile) => {
  file.streaming = false
  const index = generatedFiles.value.findIndex(item => item.path === file.path)
  if (index >= 0) generatedFiles.value[index] = file
  else generatedFiles.value.push(file)
  if (!activeFilePath.value || activeFilePath.value === file.path) selectGeneratedFile(file)
}

const appendGeneratedFileDelta = (delta: GeneratedFileDelta) => {
  if (!delta.path) return
  const index = generatedFiles.value.findIndex(item => item.path === delta.path)
  let file = index >= 0 ? generatedFiles.value[index] : undefined
  if (!file) {
    file = {
      path: delta.path,
      content: '',
      language: delta.language || 'text',
      operation: delta.operation,
      streamId: delta.streamId,
      streaming: true,
    }
    generatedFiles.value.push(file)
  }
  if (delta.reset) file.content = ''
  file.content += delta.delta || ''
  file.language = delta.language || file.language
  file.operation = delta.operation || file.operation
  file.streamId = delta.streamId || file.streamId
  file.streaming = true

  if (!activeFilePath.value) {
    selectGeneratedFile(file)
    workspaceMode.value = 'code'
  } else if (activeFilePath.value === file.path && !codeDirty.value) {
    editingCode.value = file.content
  }
}

const finishFileStreaming = () => {
  generatedFiles.value.forEach((file) => {
    file.streaming = false
  })
}

const refreshGeneratedFiles = async () => {
  if (!appId.value) return
  try {
    const filesRes = await listAppCodeFiles({ appId: String(appId.value) })
    if (filesRes.data.code !== 0) return
    const incoming = (filesRes.data.data || []) as GeneratedFile[]
    incoming.forEach((file) => {
      const current = generatedFiles.value.find((item) => item.path === file.path)
      if (current) {
        current.content = file.content
        current.language = file.language
        current.streaming = isGenerating.value && latestGenerationTask.value?.currentFile === file.path
      } else {
        generatedFiles.value.push(file)
      }
    })
    if (!activeFilePath.value && generatedFiles.value.length) selectGeneratedFile(generatedFiles.value[0])
    if (activeFile.value && !codeDirty.value) editingCode.value = activeFile.value.content
  } catch (error) {
    console.warn('同步生成文件失败', error)
  }
}

const stopTaskPolling = () => {
  if (taskPollTimer) clearInterval(taskPollTimer)
  taskPollTimer = null
}

// Older tasks may contain a provider response body. Keep the UI provider-neutral
// even before those persisted records are rewritten by a new generation.
const friendlyGenerationError = (value: unknown) => {
  const detail = String(value || '')
  const normalized = detail.toLowerCase()
  if (normalized.includes('arrearage') || normalized.includes('overdue-payment')
    || normalized.includes('insufficient balance') || normalized.includes('insufficient_balance')) {
    return 'AI 服务暂时不可用，请检查模型账户余额或 API Key 配置后重试。'
  }
  if (normalized.includes('401') || normalized.includes('unauthorized')
    || normalized.includes('invalid api key') || normalized.includes('authentication')) {
    return 'AI 服务认证失败，请检查 API Key 配置后重试。'
  }
  if (normalized.includes('timeout') || normalized.includes('timed out')) {
    return 'AI 服务响应超时，请稍后重试。'
  }
  if (detail.trim().startsWith('{') || normalized.includes('request_id') || normalized.includes('chatcmpl-')) {
    return 'AI 生成服务暂时不可用，请稍后重试。'
  }
  return detail || '生成任务失败，请稍后重试'
}

const pollGenerationTask = async () => {
  if (!appId.value) return
  try {
    const res = await getLatestGenerationTask({ appId: String(appId.value) })
    if (res.data.code !== 0 || !res.data.data) return
    const previousStatus = latestGenerationTask.value?.status
    latestGenerationTask.value = res.data.data as GenerationTaskState
    if (latestGenerationTask.value.status === 'GENERATING') {
      isGenerating.value = true
      await refreshGeneratedFiles()
      return
    }
    if (previousStatus === 'GENERATING' || isGenerating.value) {
      isGenerating.value = false
      finishFileStreaming()
      activeEventSource?.close()
      activeEventSource = null
      const activeAiMessage = [...messages.value].reverse().find((item) => item.type === 'ai' && item.loading)
      if (activeAiMessage) {
        activeAiMessage.loading = false
        activeAiMessage.statusText = undefined
        if (!activeAiMessage.content) {
          activeAiMessage.content = latestGenerationTask.value.status === 'SUCCEEDED'
            ? '任务已完成。'
            : `任务${latestGenerationTask.value.currentStep || '已结束'}。`
        }
      }
      await refreshGeneratedFiles()
      updatePreview(true)
      if (latestGenerationTask.value.status === 'FAILED') {
        message.error(friendlyGenerationError(latestGenerationTask.value.errorMessage))
      }
    }
    stopTaskPolling()
    if (latestGenerationTask.value.taskType === 'AUTO_REPAIR' && repairActive.value
        && latestGenerationTask.value.status === 'SUCCEEDED') {
      void handleRepairRoundCompleted()
    }
  } catch (error) {
    console.warn('同步生成任务状态失败', error)
  }
}

const startTaskPolling = () => {
  if (taskPollTimer) return
  void pollGenerationTask()
  taskPollTimer = setInterval(() => void pollGenerationTask(), 1500)
}

const recoverGenerationTask = async () => {
  if (!appId.value || taskRecoveryStarted || !isOwner.value) return
  taskRecoveryStarted = true
  await pollGenerationTask()
  const task = latestGenerationTask.value
  if (!task) return
  if (task.taskType === 'AUTO_REPAIR') {
    repairActive.value = task.buildStatus !== 'PASSED'
    repairAttempt.value = task.attempt || 1
    if (task.buildStatus) {
      repairRounds.value = [{
        attempt: task.attempt || 1,
        success: task.buildStatus === 'PASSED',
        stage: task.buildStatus,
        durationMs: 0,
      }]
    }
    if (task.status === 'SUCCEEDED') {
      if (task.buildStatus === 'FAILED' && (task.attempt || 1) < (task.maxAttempts || maxRepairAttempts)) {
        await runBuildRepairRound((task.attempt || 1) + 1)
      } else if (!task.buildStatus) {
        await handleRepairRoundCompleted()
      }
      return
    }
  }
  if (task.status !== 'GENERATING') return
  isGenerating.value = true
  let aiMessageIndex = messages.value.findIndex((item) => item.type === 'ai' && item.loading)
  if (aiMessageIndex < 0) {
    aiMessageIndex = messages.value.length
    messages.value.push({
      type: 'ai',
      content: '已恢复正在运行的生成任务，正在同步最新文件...',
      loading: true,
      statusText: task.currentStep || '正在恢复任务...',
    })
  }
  startTaskPolling()
  await generateCode('', aiMessageIndex, false, undefined, false, true,
    task.taskType === 'AUTO_REPAIR' ? task.attempt || 1 : 0)
}

watch(
  () => `${fileSearch.value}|${generatedFiles.value.map((file) => file.path).join('|')}`,
  () => {
    if (fileSearch.value.trim()) {
      expandedTreeKeys.value = collectFolderKeys(filteredFileTree.value)
    }
  },
)

const onCodeChange = () => {
  codeDirty.value = true
}

const formatDuration = (durationMs?: number) => {
  if (durationMs == null) return ''
  if (durationMs < 1000) return `${durationMs} ms`
  return `${(durationMs / 1000).toFixed(durationMs < 10_000 ? 1 : 0)} s`
}

const runBuildCheck = async (openPanel = true, allowDuringRepair = false) => {
  if (!appId.value || buildChecking.value || (isGenerating.value && !allowDuringRepair)) return null
  buildChecking.value = true
  if (openPanel) {
    debugPanelOpen.value = true
    activeDebugTab.value = 'build'
  }
  try {
    const res = await checkAppBuild({ appId: String(appId.value) }, { timeout: 600_000 })
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '构建检查失败')
    }
    buildResult.value = res.data.data as BuildCheckResult
    if (openPanel) {
      if (buildResult.value.success) message.success('构建检查通过')
      else if (buildResult.value.stage === 'PROJECT') {
        message.error('项目文件尚未生成，请重新生成项目')
      } else message.error('构建失败，可查看日志或使用 AI 自动修复')
    } else if (!buildResult.value.success) {
      debugPanelOpen.value = true
      activeDebugTab.value = 'build'
    }
    return buildResult.value
  } catch (error: any) {
    message.error(error?.message || '构建检查失败')
    return null
  } finally {
    buildChecking.value = false
  }
}

const startBuildRepair = async () => {
  if (!appId.value || isGenerating.value || !buildResult.value
      || buildResult.value.success || !canAutoRepairBuild.value) return
  Modal.confirm({
    title: '启动 AI 自动修复',
    content: '系统将执行“AI 修改 → 构建检查”的闭环，最多 3 轮。每轮都会创建独立版本，可在版本历史中查看 Diff 或回滚。',
    okText: '开始修复',
    cancelText: '取消',
    onOk: () => {
      repairRounds.value = []
      repairActive.value = true
      void runBuildRepairRound(1)
    },
  })
}

const regenerateMissingProject = async () => {
  if (!appInfo.value?.initPrompt || isGenerating.value) return
  repairActive.value = false
  repairRounds.value = []
  await sendInitialMessage(appInfo.value.initPrompt)
}

const runBuildRepairRound = async (attempt: number) => {
  if (!appId.value || !repairActive.value || attempt > maxRepairAttempts) return
  repairAttempt.value = attempt
  messages.value.push({ type: 'user', content: '自动修复构建错误' })
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
    statusText: '正在读取构建日志并定位错误...',
  })
  isGenerating.value = true
  if (appInfo.value) appInfo.value.generationStatus = 'GENERATING'
  workspaceMode.value = 'code'
  await nextTick()
  scrollToBottom()
  await generateCode('', aiMessageIndex, false, undefined, true, false, attempt)
}

const handleRepairRoundCompleted = async () => {
  if (repairCompletionHandling) return
  repairCompletionHandling = true
  try {
  const result = await runBuildCheck(false, true)
  if (!result) {
    repairActive.value = false
    return
  }
  repairRounds.value.push({
    attempt: repairAttempt.value,
    success: result.success,
    stage: result.stage,
    durationMs: result.durationMs,
  })
  if (result.success) {
    repairActive.value = false
    message.success(`AI 自动修复成功，共执行 ${repairAttempt.value} 轮`)
    return
  }
  if (repairAttempt.value >= maxRepairAttempts) {
    repairActive.value = false
    debugPanelOpen.value = true
    activeDebugTab.value = 'build'
    message.error('已达到 3 轮自动修复上限，请查看构建日志后手动调整')
    return
  }
  message.info(`第 ${repairAttempt.value} 轮未通过，正在开始下一轮修复`)
  await runBuildRepairRound(repairAttempt.value + 1)
  } finally {
    repairCompletionHandling = false
  }
}

const saveEditedCode = async () => {
  if (!activeFile.value || !appId.value || !codeDirty.value) return
  savingCode.value = true
  try {
    const res = await saveAppCodeFile({
      appId: String(appId.value),
      filePath: activeFile.value.path,
      content: editingCode.value,
    })
    if (res.data.code !== 0) throw new Error(res.data.message || '文件保存失败')
    activeFile.value.content = editingCode.value
    codeDirty.value = false
    message.success('文件已保存，预览即将刷新')
    updatePreview(true)
  } catch (error: any) {
    message.error(error?.message || '文件保存失败')
  } finally {
    savingCode.value = false
  }
}

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)
const ragToggleLoading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
  onElementChanged: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

const toggleRagMemory = async (enabled: boolean) => {
  if (!appId.value || !appInfo.value || ragToggleLoading.value) return
  ragToggleLoading.value = true
  try {
    const res = await toggleAppRag({ appId: String(appId.value), enabled })
    if (res.data.code !== 0) throw new Error(res.data.message || '更新 RAG 设置失败')
    appInfo.value.ragEnabled = enabled
    message.success(enabled ? 'RAG 已开启' : 'RAG 已关闭')
  } catch (error: any) {
    message.error(error?.response?.data?.message || error?.message || '更新 RAG 设置失败')
  } finally {
    ragToggleLoading.value = false
  }
}

const canResumeGeneration = computed(() => {
  const entryFile = appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT ? 'package.json' : 'index.html'
  return isOwner.value && !isGenerating.value && appInfo.value?.generationStatus === 'CANCELLED'
    && generatedFiles.value.some((file) => file.path === entryFile)
})
const canRegenerateMissingProject = computed(() => {
  const status = appInfo.value?.generationStatus
  const entryFile = appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT ? 'package.json' : 'index.html'
  return isOwner.value && !isGenerating.value && (status === 'CANCELLED' || status === 'FAILED')
    && !generatedFiles.value.some((file) => file.path === entryFile)
})

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
            .map((chat) => ({
              type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
              content: chat.messageType === 'user'
                ? (chat.message || '')
                : friendlyGenerationError(chat.message),
              createTime: chat.createTime,
            }))
            .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      try {
        const filesRes = await listAppCodeFiles({ appId: id })
        if (filesRes.data.code === 0) {
          generatedFiles.value = (filesRes.data.data || []) as GeneratedFile[]
          if (generatedFiles.value.length) selectGeneratedFile(generatedFiles.value[0])
        }
      } catch (filesError) {
        console.warn('加载代码文件失败', filesError)
      }
      try {
        const versionRes = await listAppVersions({ appId: id })
        if (versionRes.data.code === 0) versions.value = versionRes.data.data || []
      } catch (versionError) {
        console.warn('加载版本列表失败', versionError)
      }

      // 先加载对话历史
      await loadChatHistory()
      await recoverGenerationTask()
      // 如果有至少2条对话记录，展示对应的网站
      if (messages.value.length >= 2) {
        updatePreview()
      }
      // 检查是否需要自动发送初始提示词
      // 只有在是自己的应用且没有对话历史时才自动发送
      if (
          appInfo.value.initPrompt &&
          isOwner.value &&
          messages.value.length === 0 &&
          historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

const rollbackToVersion = (version: any) => {
  if (!appId.value || !version?.id || version.versionNumber === appInfo.value?.versionNumber) return
  Modal.confirm({
    title: '确认回滚版本',
    content: `确定将应用回滚到 v${version.versionNumber} 吗？当前生成目录和已部署文件会替换为该版本。`,
    okText: '回滚',
    cancelText: '取消',
    async onOk() {
      rollingBackVersionId.value = String(version.id)
      try {
        const res = await rollbackAppVersion({
          appId: String(appId.value),
          versionId: String(version.id),
        })
        if (res.data.code !== 0) {
          throw new Error(res.data.message || '版本回滚失败')
        }
        message.success(`已回滚到 v${version.versionNumber}`)
        await fetchAppInfo()
        updatePreview(true)
      } catch (error: any) {
        console.error('版本回滚失败:', error)
        message.error(error?.message || '版本回滚失败，请稍后重试')
      } finally {
        rollingBackVersionId.value = null
      }
    },
  })
}

// 发送初始消息
const showVersionDiff = async (version: any) => {
  if (!appId.value || !version?.id) return
  diffBaseVersionId.value = String(version.id)
  diffTargetVersionId.value = 'current'
  diffVisible.value = true
  await loadVersionDiff()
}

const openVersionCompare = async () => {
  if (!readyVersions.value.length) return
  const currentVersion = readyVersions.value.find((version) => version.versionNumber === appInfo.value?.versionNumber)
  diffBaseVersionId.value = String(readyVersions.value[readyVersions.value.length - 1]?.id
    || readyVersions.value[0].id)
  diffTargetVersionId.value = currentVersion ? String(currentVersion.id) : 'current'
  diffVisible.value = true
  await loadVersionDiff()
}

const loadVersionDiff = async () => {
  if (!appId.value || !diffBaseVersionId.value) return
  diffLoading.value = true
  try {
    const params: Record<string, string> = {
      appId: String(appId.value),
      baseVersionId: diffBaseVersionId.value,
    }
    if (diffTargetVersionId.value !== 'current') params.targetVersionId = diffTargetVersionId.value
    const res = await diffAppVersion(params as any)
    if (res.data.code !== 0) throw new Error(res.data.message || '加载版本差异失败')
    versionDiffs.value = (res.data.data || []) as CodeFileDiff[]
    activeDiffPath.value = versionDiffs.value[0]?.path || ''
  } catch (error: any) {
    message.error(error?.message || '加载版本差异失败')
  } finally {
    diffLoading.value = false
  }
}

const restoreActiveDiffFile = () => {
  if (!activeVersionDiff.value || !diffBaseVersionId.value || diffTargetVersionId.value !== 'current') return
  Modal.confirm({
    title: '恢复此文件',
    content: `确定将 ${activeVersionDiff.value.path} 恢复到所选基准版本吗？系统会构建项目并创建一个新版本。`,
    okText: '恢复文件',
    cancelText: '取消',
    onOk: async () => {
      restoringDiffFile.value = true
      try {
        const res = await restoreAppVersionFile({
          appId: String(appId.value),
          versionId: diffBaseVersionId.value,
          filePath: activeVersionDiff.value!.path,
        }, { timeout: 600_000 })
        if (res.data.code !== 0) throw new Error(res.data.message || '恢复文件失败')
        message.success('文件已恢复并创建新版本')
        await refreshGeneratedFiles()
        await fetchAppInfo()
        await loadVersionDiff()
        updatePreview(true)
      } catch (error: any) {
        message.error(error?.message || '恢复文件失败')
      } finally {
        restoringDiffFile.value = false
      }
    },
  })
}

const requestGenerationPlan = async (prompt: string) => {
  if (!appId.value || !prompt.trim()) return true
  planningGeneration.value = true
  try {
    const res = await createGenerationPlan({ appId: String(appId.value), prompt: prompt.trim() })
    if (res.data.code !== 0) throw new Error(res.data.message || '生成计划失败')
    const plan = res.data.data || {}
    const dataModels = Array.isArray(plan.dataModels) ? plan.dataModels.map((model: any) => ({
      modelKey: String(model?.modelKey || ''),
      displayName: String(model?.displayName || ''),
      publicOperations: Array.isArray(model?.publicOperations)
        ? model.publicOperations.filter((value: string) => ['READ', 'CREATE', 'UPDATE', 'DELETE'].includes(value))
        : [],
      fields: Array.isArray(model?.fields) ? model.fields.map((field: any) => ({
        key: String(field?.key || ''), name: String(field?.name || ''),
        type: planFieldTypes.has(field?.type as PlanFieldType) ? field.type : 'STRING',
        required: Boolean(field?.required), maxLength: field?.maxLength,
        options: Array.isArray(field?.options) ? field.options.map(String) : [],
      })) : [],
    })) : []
    generationPlan.value = {
      title: String(plan.title || appInfo.value?.appName || '新应用'),
      summary: String(plan.summary || prompt),
      pages: Array.isArray(plan.pages) ? plan.pages.map(String) : [],
      features: Array.isArray(plan.features) ? plan.features.map(String) : [],
      techStack: Array.isArray(plan.techStack) ? plan.techStack.map(String) : [],
      needsImages: Boolean(plan.needsImages),
      needsDatabase: Boolean(plan.needsDatabase),
      dataModels,
    }
  } catch (error: any) {
    message.error(friendlyGenerationError(error?.response?.data?.message || error?.message))
    return false
  } finally {
    planningGeneration.value = false
  }
  return true
}

const sendInitialMessage = async (prompt: string) => {
  if (!await requestGenerationPlan(prompt)) return
  const confirmedPlan = generationPlan.value ? { ...generationPlan.value } : undefined
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
    statusText: '正在分析需求并规划应用结构...',
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  if (appInfo.value) appInfo.value.generationStatus = 'GENERATING'
  await generateCode(prompt, aiMessageIndex, false, confirmedPlan)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value || planningGeneration.value) {
    return
  }

  let message = userInput.value.trim()
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }
  if (!await requestGenerationPlan(message)) return
  const confirmedPlan = generationPlan.value ? { ...generationPlan.value } : undefined
  userInput.value = ''
  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: message,
  })

  // 发送消息后，清除选中元素并退出编辑模式
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
    statusText: '正在分析需求并规划应用结构...',
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  if (appInfo.value) appInfo.value.generationStatus = 'GENERATING'
  await generateCode(message, aiMessageIndex, false, confirmedPlan)
}

const cancelGeneration = async () => {
  if (!appId.value || !isGenerating.value) return
  try {
    await request.post('/app/chat/cancel', null, { params: { appId: appId.value } })
    activeEventSource?.close()
    activeEventSource = null
    if (appInfo.value) appInfo.value.generationStatus = 'CANCELLED'
  } catch (error) {
    console.error('停止生成失败:', error)
  } finally {
    isGenerating.value = false
    finishFileStreaming()
    const activeAiMessage = [...messages.value]
      .reverse()
      .find((item) => item.type === 'ai' && item.loading)
    if (activeAiMessage) {
      activeAiMessage.loading = false
      activeAiMessage.statusText = undefined
      if (!activeAiMessage.content) activeAiMessage.content = '生成已停止。'
    }
    message.info('已停止生成')
  }
}

const resumeCodeGeneration = async () => {
  if (!appId.value || !canResumeGeneration.value) return

  messages.value.push({
    type: 'user',
    content: '继续上一次中止的生成任务',
  })
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
    statusText: '正在检查已生成文件并恢复任务...',
  })

  isGenerating.value = true
  if (appInfo.value) appInfo.value.generationStatus = 'GENERATING'
  await nextTick()
  scrollToBottom()
  await generateCode('', aiMessageIndex, true)
}

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (
  userMessage: string,
  aiMessageIndex: number,
  resume = false,
  confirmedPlan?: GenerationPlan,
  autoRepair = false,
  observeExisting = false,
  repairRound = 0,
) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false
  let startedByPost = false
  const isRepairStream = autoRepair || (observeExisting && repairActive.value && repairRound > 0)

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // Prompts and editable plans can be large. Start with JSON, then use EventSource only
    // to subscribe to the backend-owned task with a short appId URL.
    if (!resume && !autoRepair && !observeExisting) {
      const startRes = await startCodeGeneration({
        appId: appId.value || '',
        message: userMessage,
        plan: confirmedPlan,
      })
      if (startRes.data.code !== 0) throw new Error(startRes.data.message || '启动生成失败')
      observeExisting = true
      startedByPost = true
    }

    // 构建URL参数
    const params = new URLSearchParams({ appId: appId.value || '' })
    if (autoRepair) {
      params.set('attempt', String(repairRound || 1))
      params.set('maxAttempts', String(maxRepairAttempts))
    }
    const endpoint = autoRepair
      ? '/app/chat/repair/build'
      : observeExisting ? '/app/chat/task/stream'
        : resume ? '/app/chat/resume' : '/app/chat/gen/code'
    const url = `${baseURL}${endpoint}?${params}`

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })
    activeEventSource = eventSource
    startTaskPolling()

    let fullContent = ''
    let generatedFileCount = 0
    let renderTimer: ReturnType<typeof setTimeout> | null = null
    let pendingContent = ''

    const currentAiMessage = () => messages.value[aiMessageIndex]

    // SSE 会以很小的片段频繁返回，合并更新可避免 Markdown/代码高亮反复阻塞主线程。
    const flushContent = () => {
      if (renderTimer) {
        clearTimeout(renderTimer)
        renderTimer = null
      }
      if (pendingContent !== '') {
        currentAiMessage().content = pendingContent
        pendingContent = ''
      }
    }

    const scheduleContentUpdate = () => {
      pendingContent = fullContent
      if (!renderTimer) {
        renderTimer = setTimeout(() => {
          renderTimer = null
          currentAiMessage().content = pendingContent
          pendingContent = ''
          scrollToBottom()
        }, 50)
      }
    }

    const appendFileProgress = (file: GeneratedFile) => {
      generatedFileCount += 1
      const operationLabel = file.operation === 'modify' ? '已更新' : '已创建'
      const filePath = file.path || `文件 ${generatedFileCount}`
      fullContent += `${fullContent ? '\n\n' : ''}- ${operationLabel} \`${filePath}\``
      currentAiMessage().statusText = `已处理 ${generatedFileCount} 个文件，正在继续生成...`
      scheduleContentUpdate()
    }

    // 工具调用通常要在首个文件内容构造完成后才返回，先明确展示当前阶段。
    currentAiMessage().content = observeExisting && !startedByPost
      ? '已重新连接任务，正在同步后续生成内容...'
      : autoRepair
      ? '正在根据构建日志检查项目文件...'
      : resume ? '正在读取现有项目并定位未完成内容...' : '正在分析需求并规划页面结构...'
    currentAiMessage().statusText = observeExisting && !startedByPost
      ? latestGenerationTask.value?.currentStep || '正在恢复实时输出...'
      : autoRepair
      ? '正在定位构建错误...'
      : resume ? '正在恢复生成上下文...' : '正在准备第一个文件...'

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        // 拼接内容
        if (content !== undefined && content !== null) {
          let structured: any = null
          if (typeof content === 'string' && content.trim().startsWith('{')) {
            try { structured = JSON.parse(content) } catch { structured = null }
          }
          if (structured?.type === 'code_file') {
            const generatedFile = structured as GeneratedFile
            upsertGeneratedFile(generatedFile)
            appendFileProgress(generatedFile)
            return
          }
          if (structured?.type === 'code_file_delta') {
            appendGeneratedFileDelta(structured as GeneratedFileDelta)
            currentAiMessage().statusText = `正在流式生成 ${structured.path || '代码文件'}...`
            return
          }
          fullContent += String(content)
          currentAiMessage().statusText = '正在生成应用内容...'
          scheduleContentUpdate()
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      if (!fullContent.trim()) {
        fullContent = generatedFileCount
          ? `生成完成，共处理 ${generatedFileCount} 个文件。`
          : '应用生成完成。'
        scheduleContentUpdate()
      }
      flushContent()
      currentAiMessage().loading = false
      currentAiMessage().statusText = undefined
      finishFileStreaming()
      isGenerating.value = false
      eventSource?.close()
      activeEventSource = null

      // 延迟更新预览，确保后端已完成处理
      setTimeout(async () => {
        if (isRepairStream) {
          await refreshGeneratedFiles()
          updatePreview(true)
          await handleRepairRoundCompleted()
        } else {
          await fetchAppInfo()
          updatePreview(true)
          await runBuildCheck(false)
        }
      }, 1000)
    })

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = friendlyGenerationError(errorData.message)
        messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
        messages.value[aiMessageIndex].loading = false
        message.error(errorMessage)

        streamCompleted = true
        finishFileStreaming()
        isGenerating.value = false
        repairActive.value = false
        if (appInfo.value) appInfo.value.generationStatus = 'FAILED'
        eventSource?.close()
        activeEventSource = null
        void fetchAppInfo()
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return
      streamCompleted = true
      eventSource?.close()
      activeEventSource = null
      currentAiMessage().statusText = '实时连接中断，正在重新连接...'
      setTimeout(() => {
        if (isGenerating.value) {
          void generateCode('', aiMessageIndex, false, undefined, false, true, repairRound)
        }
      }, 800)
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
  messages.value[aiMessageIndex].loading = false
  finishFileStreaming()
  message.error('生成失败，请重试')
  isGenerating.value = false
  if (appInfo.value) appInfo.value.generationStatus = 'FAILED'
  void fetchAppInfo()
}

// 更新预览
const updatePreview = (cacheBust = false) => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
    previewUrl.value = cacheBust ? `${newPreviewUrl}?v=${Date.now()}` : newPreviewUrl
    previewReady.value = true
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    // 清理
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const pauseDeployment = async () => {
  if (!appId.value) return
  try {
    await stopDeployment({ appId: String(appId.value) })
    if (appInfo.value) appInfo.value.deployStatus = 'PAUSED'
    message.success('暂停成功')
  } catch (error) {
    console.error('暂停部署失败:', error)
    message.error('暂停部署失败')
  }
}

const resumeDeployment = async () => {
  if (!appId.value) return
  try {
    await startDeployment({ appId: String(appId.value) })
    if (appInfo.value) appInfo.value.deployStatus = 'RUNNING'
    message.success('恢复成功')
  } catch (error) {
    console.error('恢复部署失败:', error)
    message.error('恢复部署失败')
  }
}

const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

const refreshPreview = () => {
  previewLogs.value = []
  updatePreview(true)
}

const clearPreviewLogs = () => {
  const kind = activeDebugTab.value
  previewLogs.value = previewLogs.value.filter((entry) => entry.kind !== kind)
}

const handleWindowMessage = (event: MessageEvent) => {
  visualEditor.handleIframeMessage(event)
  if (event.source !== previewIframe.value?.contentWindow) return
  const data = event.data
  if (!data || data.channel !== 'yu-preview-debug') return
  const kind = data.kind === 'network' ? 'network' : 'console'
  const level = ['log', 'info', 'warn', 'error'].includes(data.level) ? data.level : 'info'
  previewLogs.value.push({
    id: ++previewLogSequence,
    kind,
    level,
    message: String(data.message || '').slice(0, 4000),
    time: new Date(data.timestamp || Date.now()).toLocaleTimeString('zh-CN', { hour12: false }),
    file: data.file ? String(data.file) : undefined,
  })
  if (previewLogs.value.length > 300) previewLogs.value.splice(0, previewLogs.value.length - 300)
}

const openDebugEntryFile = (entry: PreviewDebugEntry) => {
  const normalizedFile = entry.file?.replace(/\\/g, '/').split('?')[0]
  const file = generatedFiles.value.find((item) => {
    const normalizedPath = item.path.replace(/\\/g, '/')
    return Boolean(normalizedFile?.endsWith(normalizedPath)
      || normalizedFile?.endsWith(`/${normalizedPath.split('/').pop()}`)
      || entry.message.includes(normalizedPath))
  })
  if (file) {
    openGeneratedFile(file)
    return
  }
  message.info('该日志未包含可定位的源文件信息')
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = previewIframe.value
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = previewIframe.value
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()
  window.addEventListener('message', handleWindowMessage)
})

// 清理资源
onUnmounted(() => {
  stopResize()
  activeEventSource?.close()
  activeEventSource = null
  stopTaskPolling()
  window.removeEventListener('message', handleWindowMessage)
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.version-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 140px;
  gap: 16px;
  padding: 4px 0;
}

.app-name {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-right {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.rag-toggle {
  height: 32px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #475467;
  background: #f8faf9;
  border: 1px solid #e2e8e6;
  border-radius: 6px;
  font-size: 13px;
  white-space: nowrap;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(280px, var(--chat-width, 40%)) 8px minmax(420px, 1fr);
  padding: 8px;
  overflow: hidden;
}

.resize-handle {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 8px;
  min-width: 8px;
  height: 100%;
  cursor: col-resize;
  touch-action: none;
  outline: none;
}

.resize-handle::before {
  position: absolute;
  inset: 0 2px;
  content: '';
  border-radius: 3px;
  transition: background-color 0.15s ease;
}

.resize-handle:hover::before,
.resize-handle:focus-visible::before,
:global(body.is-resizing-columns) .resize-handle::before {
  background: #d0d5dd;
}

.resize-handle-grip {
  position: relative;
  z-index: 1;
  width: 3px;
  height: 36px;
  border-radius: 3px;
  background: #c5cad3;
  opacity: 0;
  transition: opacity 0.15s ease;
}

:global(body.is-resizing-columns) {
  cursor: col-resize !important;
  user-select: none;
}

.resize-handle:hover .resize-handle-grip,
.resize-handle:focus-visible .resize-handle-grip,
:global(body.is-resizing-columns) .resize-handle-grip {
  opacity: 1;
}

/* 左侧对话区域 */
.chat-section {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.persistent-task-status {
  margin-bottom: 10px;
  padding: 9px 11px;
  background: #f5f8ff;
  border: 1px solid #d6e4ff;
  border-radius: 6px;
}

.task-status-heading {
  display: flex;
  justify-content: space-between;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
}

.task-current-file {
  margin-top: 3px;
  overflow: hidden;
  color: #667085;
  font: 11px ui-monospace, SFMono-Regular, Menlo, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-right: 50px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}

/* 右侧预览区域 */
.preview-section {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  min-height: 56px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid #e8e8e8;
}

.workspace-heading {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.workspace-heading h3 {
  margin: 0;
  overflow: hidden;
  color: #1d2939;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-heading span {
  color: #98a2b3;
  font-size: 11px;
  white-space: nowrap;
}

.workspace-switch {
  height: 38px;
  padding: 3px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  background: #f2f4f7;
  border: 1px solid #e4e7ec;
  border-radius: 7px;
}

.workspace-switch-button {
  width: 34px;
  height: 30px;
  padding: 0;
  display: inline-grid;
  place-items: center;
  color: #667085;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 5px;
  cursor: pointer;
  font-size: 15px;
}

.workspace-switch-button:hover {
  color: #175cd3;
  background: #ffffff;
}

.workspace-switch-button.active {
  color: #175cd3;
  background: #ffffff;
  border-color: #d0d5dd;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.08);
}

.preview-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.preview-actions :deep(.ant-btn) {
  width: 30px;
  height: 30px;
  padding: 0;
}

.preview-device-switch {
  height: 32px;
  padding: 2px;
  display: inline-flex;
  gap: 1px;
  background: #f2f4f7;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
}

.preview-device-switch button {
  width: 27px;
  height: 26px;
  display: inline-grid;
  place-items: center;
  padding: 0;
  color: #667085;
  background: transparent;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}

.preview-device-switch button:hover,
.preview-device-switch button.active {
  color: #175cd3;
  background: #ffffff;
}

.preview-device-switch button.active {
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.12);
}

.build-status-label.success { color: #039855; }
.build-status-label.error { color: #d92d20; }

.code-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(180px, var(--explorer-width, 238px)) 8px minmax(0, 1fr);
  background: #ffffff;
}

.file-explorer {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e4e7ec;
}

.explorer-resize-handle {
  height: 100%;
}

.explorer-mode-switch {
  height: 38px;
  margin: 7px 8px 5px;
  padding: 3px;
  display: flex;
  align-items: center;
  gap: 2px;
  background: #f2f4f7;
  border-radius: 7px;
}

.explorer-mode-switch button {
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #667085;
  background: transparent;
  border: 0;
  border-radius: 5px;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}

.explorer-mode-switch button:hover {
  color: #344054;
}

.explorer-mode-switch button.active {
  color: #101828;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.08);
}

.file-search {
  width: auto;
  margin: 2px 8px 7px;
}

.file-tree-scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 0 0 12px;
  overflow: auto;
}

:deep(.file-tree-scroll .ant-tree) {
  background: transparent;
  color: #475467;
  font-size: 14px;
}

:deep(.file-tree-scroll .ant-tree-treenode) {
  width: 100%;
  min-height: 29px;
  padding: 0 5px;
  align-items: center;
}

:deep(.file-tree-scroll .ant-tree-node-content-wrapper) {
  min-width: 0;
  flex: 1;
  min-height: 29px;
  line-height: 29px;
  overflow: hidden;
  border-radius: 0;
}

:deep(.file-tree-scroll .ant-tree-node-selected) {
  color: #344054 !important;
  background: transparent !important;
}

:deep(.file-tree-scroll .ant-tree.ant-tree-directory .ant-tree-treenode-selected::before) {
  background: #eef0f4 !important;
}

:deep(.file-tree-scroll .ant-tree.ant-tree-directory .ant-tree-treenode-selected .ant-tree-switcher),
:deep(.file-tree-scroll .ant-tree.ant-tree-directory .ant-tree-treenode-selected .ant-tree-node-content-wrapper) {
  color: #344054 !important;
}

:deep(.file-tree-scroll .ant-tree-switcher) {
  width: 20px;
  min-width: 20px;
  height: 29px;
  line-height: 29px;
  color: #667085;
}

:deep(.file-tree-scroll .ant-tree-indent-unit) {
  width: 17px;
}

.tree-node-title {
  min-width: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 5px;
}

.tree-node-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-file-icon {
  flex: 0 0 17px;
  width: 17px;
  display: inline-block;
  color: #98a2b3;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  text-align: center;
}

.tree-folder-icon {
  flex: 0 0 17px;
  width: 17px;
  color: #667085;
  font-size: 14px;
}

.tree-file-icon.kind-vue { color: #12a67a; }
.tree-file-icon.kind-typescript { color: #3178c6; font-size: 8px; }
.tree-file-icon.kind-javascript { color: #b58b00; font-size: 8px; }
.tree-file-icon.kind-html { color: #d95d39; }
.tree-file-icon.kind-style { color: #3976c6; }
.tree-file-icon.kind-json { color: #8a9a14; }
.tree-file-icon.kind-markdown { color: #3182a5; }
.tree-file-icon.kind-yaml { color: #a05db1; }
.tree-file-icon.kind-lock { color: #4389a2; }

.file-stream-dot {
  flex: 0 0 6px;
  width: 6px;
  height: 6px;
  background: #1677ff;
  border-radius: 50%;
  animation: file-stream-pulse 1s ease-in-out infinite;
}

@keyframes file-stream-pulse {
  50% { opacity: 0.3; }
}

.file-tree-empty,
.file-viewer-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #98a2b3;
  font-size: 12px;
}

.file-tree-empty > .anticon,
.file-viewer-empty > .anticon {
  color: #b9c0cc;
  font-size: 30px;
}

.file-viewer {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.code-editor-toolbar {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f5;
  font-size: 14px;
}

.code-file-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #344054;
}

.code-file-path {
  min-width: 0;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.language-label {
  flex: 0 0 auto;
  padding: 2px 6px;
  color: #667085;
  background: #f2f4f7;
  border-radius: 4px;
  font-size: 10px;
}

.code-streaming {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #1677ff;
  white-space: nowrap;
}

.code-dirty {
  color: #d46b08;
}

.code-editor {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: #ffffff;
}

.preview-content {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #eef1f5;
}

.preview-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: stretch;
  justify-content: center;
  overflow: auto;
}

.preview-device-frame {
  height: 100%;
  min-height: 0;
  background: #ffffff;
  transition: width 180ms ease, box-shadow 180ms ease;
}

.preview-device-frame.device-desktop {
  width: 100%;
}

.preview-device-frame.device-tablet {
  width: min(768px, 100%);
  box-shadow: 0 0 0 1px #d0d5dd, 0 8px 24px rgba(16, 24, 40, 0.1);
}

.preview-device-frame.device-mobile {
  width: min(390px, 100%);
  box-shadow: 0 0 0 1px #d0d5dd, 0 8px 24px rgba(16, 24, 40, 0.1);
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-debug-panel {
  flex: 0 0 220px;
  min-height: 150px;
  max-height: 42%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-top: 1px solid #d0d5dd;
}

.debug-panel-tabs {
  flex: 0 0 38px;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 8px;
  border-bottom: 1px solid #eaecf0;
}

.debug-panel-tabs > button:not(.ant-btn) {
  height: 38px;
  padding: 0 10px;
  color: #667085;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 12px;
}

.debug-panel-tabs > button:not(.ant-btn).active {
  color: #175cd3;
  border-bottom-color: #175cd3;
}

.debug-panel-tabs button span {
  margin-left: 5px;
  padding: 0 5px;
  background: #f2f4f7;
  border-radius: 8px;
  font-size: 10px;
}

.debug-panel-tabs :deep(.ant-btn) {
  margin-left: auto;
}

.debug-log-list,
.build-panel-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.debug-log-entry {
  width: 100%;
  min-height: 30px;
  display: grid;
  grid-template-columns: 72px 52px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  padding: 6px 10px;
  color: #344054;
  background: #ffffff;
  border: 0;
  border-bottom: 1px solid #f2f4f7;
  cursor: pointer;
  font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
  text-align: left;
}

.debug-log-entry:hover { background: #f9fafb; }
.debug-log-entry.level-warn { background: #fffaeb; }
.debug-log-entry.level-error { color: #b42318; background: #fef3f2; }
.debug-log-time { color: #98a2b3; }
.debug-log-level { text-transform: uppercase; }
.debug-log-message { min-width: 0; overflow-wrap: anywhere; }

.build-panel-content {
  padding: 10px 12px 12px;
}

.build-panel-summary,
.build-panel-summary > div,
.build-panel-actions {
  display: flex;
  align-items: center;
}

.build-panel-summary {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.build-panel-summary > div,
.build-panel-actions {
  gap: 8px;
}

.build-panel-summary span {
  color: #98a2b3;
  font-size: 12px;
}

.build-panel-content pre {
  margin: 0;
  padding: 10px;
  overflow: auto;
  color: #344054;
  background: #f8fafc;
  border: 1px solid #eaecf0;
  border-radius: 5px;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
}

.repair-rounds {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.repair-rounds span {
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
}

.repair-rounds .success { color: #027a48; background: #ecfdf3; }
.repair-rounds .failed { color: #b42318; background: #fef3f2; }

.preview-debug-panel :deep(.ant-empty) {
  margin: 24px 0;
}

.generation-plan :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.generation-plan :deep(.ant-form-item-label > label) {
  color: #344054;
  font-weight: 600;
}

.generation-plan-assets {
  margin-bottom: 0 !important;
}

.generation-plan-assets span {
  margin-left: 10px;
  color: #667085;
}

.plan-model-heading,
.plan-model-heading > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.plan-model-heading > div {
  align-items: flex-start;
  flex-direction: column;
  gap: 1px;
}

.plan-model-heading span {
  color: #8a909a;
  font-size: 12px;
}

.plan-model-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 370px;
  overflow: auto;
  margin-top: 10px;
}

.plan-model {
  padding: 12px;
  border: 1px solid #e1e5eb;
  border-radius: 6px;
  background: #f9fafb;
}

.plan-model-title {
  display: grid;
  grid-template-columns: 1fr 1fr 32px;
  gap: 8px;
}

.plan-permissions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px 0;
  color: #667085;
  font-size: 12px;
}

.plan-fields {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.plan-field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 112px 58px minmax(130px, 1fr) 32px;
  align-items: center;
  gap: 7px;
}

.plan-field-empty {
  color: #a0a5ad;
  text-align: center;
}

.diff-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.version-diff-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f8f9fb;
}

.version-diff-heading .ant-btn {
  margin-left: auto;
}

.version-diff-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font: 13px ui-monospace, SFMono-Regular, Menlo, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-diff-count {
  color: #667085;
  font-size: 12px;
}

.version-diff-workspace {
  height: min(68vh, 720px);
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
}

.diff-file-list {
  overflow: auto;
  border-right: 1px solid #edf0f5;
  background: #f8fafc;
}

.diff-file-list button {
  width: 100%;
  padding: 9px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #344054;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #eaecf0;
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}

.diff-file-list button:hover,
.diff-file-list button.active {
  background: #eef4ff;
}

.diff-file-list button span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diff-file-list button small {
  color: #667085;
  white-space: nowrap;
}

.diff-file-viewer {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.line-diff {
  flex: 1;
  min-height: 0;
  overflow: auto;
  color: #344054;
  background: #ffffff;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
}

.split-diff-row {
  min-width: 900px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.split-diff-row > :first-child {
  border-right: 1px solid #eaecf0;
}

.diff-line {
  min-height: 23px;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
}

.unified-diff .diff-line {
  grid-template-columns: 48px 20px minmax(0, 1fr);
}

.diff-line > span {
  padding: 2px 7px;
  color: #98a2b3;
  background: rgba(248, 250, 252, 0.8);
  border-right: 1px solid rgba(228, 231, 236, 0.8);
  text-align: right;
  user-select: none;
}

.diff-line > b {
  padding-top: 2px;
  font-weight: 400;
  text-align: center;
}

.diff-line code {
  min-width: 0;
  padding: 2px 8px;
  color: inherit;
  background: transparent;
  white-space: pre;
}

.diff-added .after,
.unified-diff .diff-added,
.diff-modified .after { background: #ecfdf3; }
.diff-deleted .before,
.unified-diff .diff-deleted,
.diff-modified .before { background: #fef3f2; }
.diff-added .before,
.diff-deleted .after { background: #f9fafb; }

.diff-hunk {
  padding: 3px 10px;
  color: #667085;
  background: #eef4ff;
  text-align: center;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  #appChatPage {
    height: calc(100vh - 68px);
    height: calc(100dvh - 68px);
  }

  .main-content {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
    gap: 8px;
  }

  .main-resize-handle,
  .explorer-resize-handle {
    display: none;
  }

  .chat-section,
  .preview-section {
    height: auto;
    min-height: 0;
  }

  .code-workspace {
    grid-template-columns: minmax(180px, 220px) minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  #appChatPage {
    height: calc(100vh - 58px);
    height: calc(100dvh - 58px);
    padding: 8px;
  }

  .header-bar {
    flex: 0 0 auto;
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
    padding: 8px;
  }

  .header-left {
    width: 100%;
    flex-wrap: wrap;
    gap: 6px;
  }

  .header-right {
    width: 100%;
    gap: 6px;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
  }

  .header-right::-webkit-scrollbar,
  .preview-actions::-webkit-scrollbar {
    display: none;
  }

  .header-action-button {
    flex: 0 0 32px;
    width: 32px;
    padding-inline: 0;
  }

  .header-action-label {
    display: none;
  }

  .app-name {
    flex: 1 0 100%;
    font-size: 16px;
  }

  .main-content {
    grid-template-rows: minmax(380px, 62dvh) minmax(420px, 72dvh);
    align-content: start;
    padding: 4px;
    padding-bottom: max(8px, env(safe-area-inset-bottom));
    gap: 8px;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
  }

  .preview-header {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto auto;
    gap: 8px;
    padding: 8px 10px;
  }

  .workspace-heading {
    display: none;
  }

  .workspace-switch {
    grid-column: 1;
    grid-row: 1;
    justify-self: start;
  }

  .preview-actions {
    width: 100%;
    grid-column: 1;
    grid-row: 2;
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 1px;
    scrollbar-width: none;
  }

  .code-workspace {
    grid-template-columns: 120px minmax(0, 1fr);
  }

  .explorer-mode-switch {
    margin-inline: 6px;
  }

  .file-search {
    margin-inline: 7px;
  }

  .code-editor {
    padding: 0;
  }

  .message-content {
    min-width: 0;
    max-width: calc(100% - 42px);
    overflow-wrap: anywhere;
  }

  .messages-container,
  .input-container {
    padding: 12px;
  }

  .input-wrapper .ant-input {
    padding-right: 11px;
  }

  .input-actions {
    position: static;
    display: flex;
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}
</style>
