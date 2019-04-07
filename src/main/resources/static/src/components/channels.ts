import Vue from "vue";
import Component from "vue-class-component";
import {Channel} from "../models/channel";
import {Watch} from "vue-property-decorator";
import {FeedItem} from "../models/feedItem";
import HttpService from "../services/httpService";

@Component({
    template: `
<v-app>
    <v-navigation-drawer dark app>
        <v-list dense class="pt-0">
            <v-list-tile @click="refresh()" :class="selectedChannel === 0 ? 'primary--text' : ''">
                <v-list-tile-action><i class="fa fa-rss" aria-hidden="true"></i></v-list-tile-action>
                <v-list-tile-content>All channels</v-list-tile-content>
            </v-list-tile>
            <v-list-tile v-for="channel in channels" :key="channel.title" @click="getChannel(channel.id)"
                          :class="selectedChannel === channel.id ? 'primary--text' : ''">
                <v-list-tile-action>
                    <i class="fa fa-rss" aria-hidden="true"></i>
                </v-list-tile-action>
                <v-list-tile-content>
                    <v-list-tile-title>{{ channel.title }}</v-list-tile-title>
                </v-list-tile-content>
            </v-list-tile>
        </v-list>
    </v-navigation-drawer> 
    <v-toolbar app dense>
        <v-btn small @click="refresh()">Refresh</v-btn>
        <v-dialog v-model="dialog" persistent max-width="290"> 
            <template v-slot:activator="{ on }">
                <v-btn small color="primary" dark v-on="on">Add feed</v-btn>
            </template> 
            <v-card>
                <v-card-title class="headline">Add feed</v-card-title>
                    <v-text-field v-model="newChannel" label="URL" style="margin: 0 5px 0 5px" single-line autofocus>
                    </v-text-field> 
                <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn color="green darken-1" flat @click="dialog = false;">Cancel</v-btn>
                <v-btn color="green darken-1" flat @click="dialog = false; addChannel()">Add</v-btn>
                </v-card-actions>
            </v-card> 
        </v-dialog>
    </v-toolbar>
    <v-content>
        <v-container fluid>
        <v-alert :value="errorMessage.length > 0" dismissible color="error" icon="warning" outline>
                {{ errorMessage }} 
        </v-alert>
        <ul>
            <li v-for="feedItem in data" class="my-3">
                <a  :id="selectedChannel + '-feed-' + feedItem.id"
                    :href="feedItem.link"
                    @click="markRead(feedItem.id)"
                    target="_blank"
                    v-bind:class="{read: feedItem.read, new: !feedItem.read}">
                    {{ feedItem.title }}
                </a>
                <p class="font-weight-black subheading">{{ feedItem.pubDate }}</p>
                <div class="body-2" v-html="feedItem.description"></div>
            </li>
        </ul>
        </v-container>
    </v-content>
</v-app>
    `
})
export default class Channels extends Vue {

    private showModal = false;

    private channels: Array<Channel> = [];

    private selectedChannel = 0;

    private data: Array<FeedItem> = [];

    private newChannel = "";

    private dialog = false;

    private errorMessage = "";

    async beforeMount(): Promise<void> {
        await this.getAllChannels();
    };

    private async refresh(): Promise<void> {
        await HttpService.sendRequest('channels/refresh');
        return this.getAllChannels();
    }

    private async getAllChannels(): Promise<void> {
        try {
            const response = await HttpService.sendRequest('/channels/all');
            this.channels = await response.json();
            this.selectedChannel = 0;
            this.channels.forEach(channel => channel.feedItems.forEach(item => this.data.push(item)));
        } catch (e) {
            this.errorMessage = e.message;
        }
    }

    private async getChannel(id: number): Promise<void> {
        try {
            const response = await HttpService.sendRequest(`/channels/get/${id}`);
            const channel: Channel = await response.json();
            this.selectedChannel = channel.id;
            this.data = channel.feedItems;
        } catch (e) {
            this.errorMessage = e.message;
        }
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
        try {
            await HttpService.sendRequest('/channels/read', configInit);
        } catch (e) {
            this.errorMessage = e.message;
        }
        const link = document.getElementById(`${this.selectedChannel}-feed-${id}`);
        if (link) {
            link.className = "read";
        }
    }

    private async addChannel(): Promise<void> {
        const configInit: RequestInit = {
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            method: 'POST',
            body: this.newChannel
        };
        try {
            await HttpService.sendRequest("/channels/add", configInit);
        } catch (e) {
            this.errorMessage = e.message;
        }
        this.showModal = false;
        await this.refresh();
    }

    @Watch('dialog')
    private onDialogVisibleChange(val: any, oldVal: any): void {
        if (val) {
            this.newChannel = "";
        }
    }
}
