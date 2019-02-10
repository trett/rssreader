import Vue from "vue";
import Header from "./components/header"
import News from "./components/news"

let v = new Vue({
    el: "#app",
    template: `
    <div>
    <Header></Header>
    <div><News></News></div>
        </div>
    `,
    components: {
        Header,
        News
    }
});