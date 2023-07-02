import Vue from 'vue'
import VueRouter from "vue-router";
import LoginComponent from './components/loginComponent';
import SettingsComponent from './components/settingsComponent';
import AlertComponent from './components/alertComponent';
import FeedsComponent from './components/feedsComponent';

Vue.use(VueRouter)

export default new VueRouter({
    routes: [
        {
            path: "/auth",
            component: LoginComponent,
        },
        {
            path: "/settings",
            component: SettingsComponent,
        },
        {
            path: "/error",
            component: AlertComponent,
        },
        {
            path: "*",
            component: FeedsComponent,
            name: "feeds",
        },
    ],
});
