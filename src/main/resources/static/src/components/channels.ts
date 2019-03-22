import Vue from "vue";
import Component from "vue-class-component";
import {Feed} from "../models/feed";
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
                    <a :id="'feed-' + feedItem.id" :href="feedItem.link" v-on:click="markRead(feedItem.id)" target="_blank"
                     v-bind:class="{read: feedItem.read, new: !feedItem.read}">{{feedItem.title}}</a>
                     <div v-html="feedItem.description"></div>
                </li>
            </div>
        </div>
    `
})
export default class Channels extends Vue {

    private showModal = false;

    private feeds: Array<Feed> = [];

    private newChannel = "";

    async beforeMount(): Promise<void> {
        await this.getNews();
    };

    private async refresh(): Promise<void> {
        const response = await fetch('channels/refresh');
        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        return this.getNews();
    }

    private async getNews(): Promise<void> {
        const response = await fetch('/channels/all');
        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        this.feeds = await response.json();
    }

    private async markRead(id: number): Promise<void> {
        const configInit: RequestInit = {
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            method: 'POST',
            body: String(id)
        };
        const response = await fetch('/channels/read', configInit);

        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        const link = document.getElementById(`feed-${id}`);
        if (link) {
            link.className = "read";
        }
    }

    private async addChannel() {
        const configInit: RequestInit = {
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            method: 'POST',
            body: this.newChannel
        };
        const response = await fetch("/channels/add", configInit);
        if (response.status !== 200) {
            throw new Error("Ошибка");
        }
        this.showModal = false;
    }
}
