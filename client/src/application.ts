import Vue from "vue";
import Main from "./layouts/main";
import vuetify from "./plugins/vuetify";
import router from './router';

const app = new Vue({
    router,
    vuetify,
    el: "#app",
    template: `<Main></Main>`,
    components: {
        Main,
    },
});
