import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {Route} from "vue-router";
import EventBus from "../eventBus";
import {FeedItem, NetworkService} from "../services/networkService";
import SettingsService from "../services/settingsService";

@Component({
    template: `
        <p v-if="!data.length" class="text-center display-1" style="color: #666666">No feeds. Refresh later</p>
        <v-container v-else fluid grid-list-md pa-0>
            <v-layout row wrap>
                <template v-for="feedItem in data">
                    <v-flex xs12>
                        <v-card tag="a"
                                @click="markRead([feedItem.id]); window.open(feedItem.link, '_blank')"
                                elevation="3"
                                v-bind:class="{read: feedItem.read}">
                            <v-card-title class="title">
                                {{ feedItem.title || (feedItem.description.substring(0, 50) + '...') }}
                            </v-card-title>
                            <v-card-text>
                                <div v-bind:class="{read: feedItem.read, unread: !feedItem.read}"
                                    v-html="feedItem.description">
                                </div>
                            </v-card-text>
                            <v-card-actions class="body-2">{{feedItem.pubDate}}</v-card-actions>
                        </v-card>
                    </v-flex>
                </template>
            </v-layout>
        </v-container>
    `,
})
export default class FeedsComponent extends Vue {

    private data: FeedItem[] = [];

    private settingsService: SettingsService;

    public async beforeMount(): Promise<void> {
        this.settingsService = new SettingsService();
        return this.setChannelData(this.$route.params.id || "");
    }

    public mounted(): void {
        EventBus.$on("updateFeeds", async () =>
            await this.setChannelData(this.$route.params.id || ""));
        EventBus.$on("markAllAsRead", async () =>
            await this.markRead(this.data.map(feedItem => feedItem.id)));
    }

    @Watch("$route")
    private async onRouteUpdate(to: Route, from: Route, next: () => void): Promise<void> {
        return this.setChannelData(to.params.id || "");
    }

    private async setChannelData(id: string) {
        try {
            const feedItems = !id || id === "all"  ?
                await NetworkService.getAllFeeds() : await NetworkService.getFeedsByChannelId(id) ;
            this.data = (await this.settingsService.getSettings()).hideRead ?
                feedItems.filter(feedItem => !feedItem.read) : feedItems;
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async markRead(ids: string[]): Promise<void> {
        try {
            await NetworkService.markRead(ids);
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        this.data.forEach(item => {
            if (ids.indexOf(item.id as string) !== -1) {
                item.read = true;
            }
        });
    }
}
