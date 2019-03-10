import Vue from "vue";
import Component from "vue-class-component";
import { Feed } from "../models/feed";
import ModalDialog from "./modalDialog";

@Component({
    components: {
        modalDialog: ModalDialog
    },
    template: `
        <div>
            <button v-on:click="refresh()">Refresh</button>
            <button v-on:click="showModal = true">Add Channel</button>
            <modalDialog v-if="showModal" @close="addChannel()">
                <h3 slot="header">Add channel</h3>
                <div slot="body">
                    <p>Put link to rss:</p>
                    <input v-model="newChannel"/>
                </div>
            </modalDialog>
            <div v-for="feed in feeds">
                <h2>{{ feed.title }}</h2>
                <li v-for="feedItem in feed.feedItems">
                    <a :href="feedItem.link">{{feedItem.title}}</a>
                </li>
            </div>
        </div>
    `
})
export default class News extends Vue {

    public showModal = false;

    feeds: Array<Feed> = [];

    newChannel = "";

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

    private async addChannel() {
        console.log(this.newChannel);
        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/channel/add");
        xhr.send(this.newChannel);
        this.showModal = false;
    }
}
