import Vue from "vue";
import Vuetify from "vuetify";
import Channels from "./components/channels";

Vue.use(Vuetify,
    { iconfont: 'fa' }
);

let v = new Vue({
    el: "#app",
    template: `
        <Channels/>
    `,
    components: {
        Channels
    }
});