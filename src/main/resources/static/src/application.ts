import Vue from "vue";
import Router from "vue-router";
import Vuetify from "vuetify";
import FeedsComponent from "./components/feedsComponent";
import SettingsComponent from "./components/settingsComponent";
import Main from "./layouts/main";
import AlertComponent from "./components/alertComponent";

Vue.use(Vuetify);
Vue.use(Router);

const router = new Router({
    routes: [
        {
            path: '/',
            component: FeedsComponent,
            props: {id: null}
        },
        {
            path: '/channel/:id',
            component: FeedsComponent
        },
        {
            path: '/settings',
            component: SettingsComponent
        },
        {
            path:'/error',
            component: AlertComponent
        }
    ]
});

new Vue({
    router,
    el: "#app",
    template: `<Main></Main>`,
    components: {
        Main
    }
});