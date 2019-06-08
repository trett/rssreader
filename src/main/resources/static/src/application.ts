import axios from "axios";
import Vue from "vue";
import Router from "vue-router";
import Vuetify from "vuetify";
import AlertComponent from "./components/alertComponent";
import FeedsComponent from "./components/feedsComponent";
import SettingsComponent from "./components/settingsComponent";
import EventBus from "./eventBus";
import Main from "./layouts/main";

axios.interceptors.response.use(function (response) {
    return response;
}, function (error) {
    EventBus.$emit("loadOff");
    if (401 === error.response.status) {
        // TODO: temp hack to refresh auth
        window.location.reload(true);
    } else {
        throw Error(error.response.headers["error-message"] as string);
    }
});

Vue.use(Vuetify);
Vue.use(Router);

const router = new Router({
    routes: [
        {
            path: '*',
            component: FeedsComponent,
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
            path: '/error',
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