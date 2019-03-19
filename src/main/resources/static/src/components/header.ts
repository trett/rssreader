import Vue from "vue";
import Component from 'vue-class-component'

@Component({
    template: `
        <h1>{{message}}</h1>
    `
})
export default class Header extends Vue {

    message = "Rss reader";
}