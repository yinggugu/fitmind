import { createRouter, createWebHashHistory } from 'vue-router'

import AssistantView from '../views/AssistantView.vue'
import BodyProfileView from '../views/BodyProfileView.vue'
import DashboardView from '../views/DashboardView.vue'
import HealthLetterView from '../views/HealthLetterView.vue'
import RecordsView from '../views/RecordsView.vue'

const routes = [
  {
    path: '/',
    component: DashboardView,
  },
  {
    path: '/weights',
    component: RecordsView,
  },
  {
    path: '/meals',
    component: RecordsView,
  },
  {
    path: '/body-profile',
    component: BodyProfileView,
  },
  {
    path: '/health-letter',
    component: HealthLetterView,
  },
  { path: '/assistant', component: AssistantView },
  { path: '/records', component: RecordsView },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export default createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})
