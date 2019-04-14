import Vue from "vue";
import Router from "vue-router";
import Vuetify from "vuetify";
import FeedsComponent from "./components/feedsComponent";
import SettingsComponent from "./components/settingsComponent";
import Main from "./layouts/main";

let ErrorHandler: any = {
    message: ""
};

ErrorHandler.install = function () {
    Vue.prototype.$setError = function (message: string) {
        ErrorHandler.message = message;
    };
    Vue.prototype.$getError = function () {
        return ErrorHandler.message;
    };
};

Vue.use(Vuetify);
Vue.use(Router);
Vue.use(ErrorHandler);

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