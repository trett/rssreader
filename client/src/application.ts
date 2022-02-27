import Vue from "vue";
import Router from "vue-router";
import AlertComponent from "./components/alertComponent";
import FeedsComponent from "./components/feedsComponent";
import SettingsComponent from "./components/settingsComponent";
import Main from "./layouts/main";
import vuetify from "./plugins/vuetify";

Vue.use(Router);

const router = new Router({
    routes: [
        {
            path: "*",
            component: FeedsComponent,
            name: "feeds",
        },
        {
            path: "/settings",
            component: SettingsComponent,
        },
        {
            path: "/error",
            component: AlertComponent,
        },
    ],
});

const app = new Vue({
    router,
    vuetify,
    el: "#app",
    template: `<Main></Main>`,
    components: {
        Main,
    },
});
