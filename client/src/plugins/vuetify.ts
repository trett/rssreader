
import "@fortawesome/fontawesome-free/css/all.css";
import "@fortawesome/fontawesome-free/js/brands";
import "@fortawesome/fontawesome-free/js/fontawesome";
import "@fortawesome/fontawesome-free/js/regular";
import "@fortawesome/fontawesome-free/js/solid";
import "material-design-icons-iconfont/dist/material-design-icons.css";
import Vue from "vue";
import Vuetify from "vuetify";
import "vuetify/dist/vuetify.min.css";
import EventBus from "../eventBus";

Vue.use(Vuetify);
Vue.config.errorHandler = (err, vm, info) => {
    EventBus.$emit("error", err.message);
};

export default new Vuetify({
    icons: {
        iconfont: "md",
    },
});
