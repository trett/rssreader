import Vue from "vue"
import Component from "vue-class-component"
import Record from "../models/record"

@Component({
    template: `
        <div>
            <button v-on:click="refresh()">Refresh</button>
            <li v-for="item in news">
                <a href={{item.link}}>{{item.text}}</a>
            </li>
        </div>
    `
})
export default class News extends Vue {
    
    news: Array<Record> = [];

    async refresh(): Promise<void> {
        const response = await fetch ('/news'); 
        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        this.news = await response.json();
    }
}