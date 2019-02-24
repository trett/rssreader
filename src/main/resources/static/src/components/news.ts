import Vue from "vue";
import Component from "vue-class-component";
import {Feed} from "../models/feed";

@Component({
    template: `
        <div>
            <button v-on:click="refresh()">Refresh</button>
            <div v-for="feed in feeds">
                <h2>{{ feed.title }}</h2>
                <li v-for="feedItem in feed.feedItems">
                    <a href={{feedItem.link}}>{{feedItem.title}}</a>
                </li>
            </div>
        </div>
    `
})
export default class News extends Vue {

    feeds: Array<Feed> = [];

    async beforeMount(): Promise<void> {
        await this.getNews();
    };

    async refresh(): Promise<void> {
        const response = await fetch('/refresh');
        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        return this.getNews();
    }

    private async getNews(): Promise<void> {
        const response = await fetch('/news');
        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        this.feeds = await response.json();
    }
}
