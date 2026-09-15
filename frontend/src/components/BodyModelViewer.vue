<script setup>
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  modelUrl: { type: String, required: true },
})

const container = ref(null)
const loading = ref(true)
const error = ref('')
let scene
let camera
let renderer
let controls
let model
let resizeObserver
let modelHeight = 1

function render() {
  if (scene && camera && renderer) renderer.render(scene, camera)
}

function setView(type = 'front') {
  if (!camera || !controls) return
  const distance = modelHeight * 1.9
  const targetY = modelHeight * 0.5
  controls.target.set(0, targetY, 0)
  if (type === 'side') camera.position.set(distance, targetY, 0)
  else camera.position.set(0, targetY, distance)
  camera.lookAt(controls.target)
  controls.update()
  render()
}

function resize() {
  if (!container.value || !camera || !renderer) return
  const width = container.value.clientWidth
  const height = container.value.clientHeight
  if (!width || !height) return
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, false)
  render()
}

function disposeObject(object) {
  object?.traverse((child) => {
    if (!child.isMesh) return
    child.geometry?.dispose()
    const materials = Array.isArray(child.material) ? child.material : [child.material]
    materials.forEach((material) => {
      if (!material) return
      Object.values(material).forEach((value) => value?.isTexture && value.dispose())
      material.dispose()
    })
  })
}

onMounted(async () => {
  await nextTick()
  if (!container.value) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color('#f7faff')
  camera = new THREE.PerspectiveCamera(32, 1, 0.01, 1000)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  container.value.appendChild(renderer.domElement)

  scene.add(new THREE.HemisphereLight(0xf8fbff, 0xb9c4cf, 2.2))
  const keyLight = new THREE.DirectionalLight(0xffffff, 2.4)
  keyLight.position.set(3, 5, 5)
  keyLight.castShadow = true
  scene.add(keyLight)
  const fillLight = new THREE.DirectionalLight(0xb8d2ef, 1.4)
  fillLight.position.set(-4, 2, 2)
  scene.add(fillLight)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enablePan = false
  controls.enableDamping = false
  controls.minDistance = 0.5
  controls.maxDistance = 20
  controls.addEventListener('change', render)

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(container.value)

  new GLTFLoader().load(
    props.modelUrl,
    (gltf) => {
      model = gltf.scene
      const initialBox = new THREE.Box3().setFromObject(model)
      const initialCenter = initialBox.getCenter(new THREE.Vector3())
      model.position.x -= initialCenter.x
      model.position.y -= initialBox.min.y
      model.position.z -= initialCenter.z
      model.traverse((child) => {
        if (child.isMesh) {
          child.castShadow = true
          child.receiveShadow = true
        }
      })
      scene.add(model)

      const fittedBox = new THREE.Box3().setFromObject(model)
      const size = fittedBox.getSize(new THREE.Vector3())
      modelHeight = Math.max(size.y, 0.1)
      camera.near = Math.max(modelHeight / 100, 0.01)
      camera.far = modelHeight * 100
      camera.updateProjectionMatrix()

      const floor = new THREE.Mesh(
        new THREE.CircleGeometry(Math.max(size.x, size.z) * 0.9, 64),
        new THREE.MeshStandardMaterial({ color: 0xe8eef4, roughness: 1 }),
      )
      floor.rotation.x = -Math.PI / 2
      floor.position.y = -0.005
      floor.receiveShadow = true
      floor.name = 'fitmind-floor'
      scene.add(floor)

      controls.minDistance = modelHeight * 0.8
      controls.maxDistance = modelHeight * 3.2
      setView('front')
      resize()
      loading.value = false
    },
    undefined,
    () => {
      loading.value = false
      error.value = '身体模型加载失败，请刷新页面重试。'
    },
  )
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  controls?.dispose()
  disposeObject(model)
  const floor = scene?.getObjectByName('fitmind-floor')
  floor?.geometry?.dispose()
  floor?.material?.dispose()
  renderer?.dispose()
  renderer?.domElement?.remove()
})
</script>

<template>
  <div class="body-model-shell">
    <div ref="container" class="body-model-canvas" aria-label="可旋转的当前身体三维模型"></div>
    <div v-if="loading" class="body-model-state"><span></span>正在加载身体模型…</div>
    <div v-else-if="error" class="body-model-state body-model-state--error">{{ error }}</div>
    <div class="body-model-toolbar">
      <button type="button" @click="setView('front')">正面</button>
      <button type="button" @click="setView('side')">侧面</button>
    </div>
    <p class="body-model-hint">拖动旋转 · 滚轮缩放</p>
  </div>
</template>
