import Vue from "vue";
import Component from "vue-class-component";
import {Feed} from "../models/feed";

@Component({
    template: `
<v-app>
    <v-navigation-drawer dark app >
        <v-list dense class="pt-0" >
            <v-list-tile v-for="feed in feeds" :key="feed.title" @click="">
                <v-list-tile-action>
                    <i class="fa fa-rss" aria-hidden="true"></i>
                </v-list-tile-action>
                <v-list-tile-content>
                    <v-list-tile-title>{{ feed.title }}</v-list-tile-title>
                </v-list-tile-content>
            </v-list-tile>
        </v-list>
    </v-navigation-drawer> 
    <v-toolbar app>
        <v-btn v-on:click="refresh()">Refresh</v-btn>
        <v-dialog v-model="dialog" persistent max-width="290"> 
            <template v-slot:activator="{ on }">
                <v-btn color="primary" dark v-on="on">Add feed</v-btn>
            </template> 
            <v-card>
                <v-card-title class="headline">Add feed</v-card-title>
                    <v-text-field v-model="newChannel" label="URL" style="margin: 0 5px 0 5px" single-line autofocus>
                    </v-text-field> 
                <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn color="green darken-1" flat @click="dialog = false">Cancel</v-btn>
                <v-btn color="green darken-1" flat @click="dialog = false; addChannel()">Add</v-btn>
                </v-card-actions>
            </v-card> 
        </v-dialog>
    </v-toolbar>
    <v-content>
        <v-container fluid>
        <div v-for="(feed, index) in feeds">
                <li v-for="feedItem in feed.feedItems">
                    <a :id="'feed-' + feedItem.id" :href="feedItem.link" v-on:click="markRead(feedItem.id)" target="_blank"
                    v-bind:class="{read: feedItem.read, new: !feedItem.read}">{{feedItem.title}}</a>
                    <div v-html="feedItem.description"></div>
                <hr style="margin: 10px 0 10px 0"/>
            </li>
        </div>
        </v-container>
    </v-content>
    <v-footer app>Design by trett &copy; {{ new Date().getFullYear() }}</v-footer>
</v-app>
    `
})
export default class Channels extends Vue {

    private showModal = false;

    private feeds: Array<Feed> = [];

    private newChannel = "";

    private dialog = false;

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
