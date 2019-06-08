import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {Route} from "vue-router";
import EventBus from "../eventBus";
import {FeedItem, NetworkService} from "../services/networkService";
import SettingsService from "../services/settingsService";

@Component({
    template: `
        <p v-if="!data.length" class="text-xs-center" style="color: #666666">No feeds. Refresh later</p>
        <template v-else>
            <ul class="feed-list">
                <li v-for="feedItem in data" class="my-3">
                    <a  :href="feedItem.link"
                        @click="markRead([feedItem.id])"
                        target="_blank"
                        v-bind:class="{read: feedItem.read, new: !feedItem.read}">
                        {{ feedItem.title || (feedItem.description.substring(0, 50) + '...') }}
                    </a>
                    <p class="font-weight-black body-2">{{ feedItem.pubDate }}</p>
                    <div class="body-2" v-html="feedItem.description"></div>
                </li>
            </ul>
        </template>
    `
})
export default class FeedsComponent extends Vue {

    private data: Array<FeedItem> = [];

    private settingsService: SettingsService;

    async beforeMount(): Promise<void> {
        this.settingsService = new SettingsService();
        return this.setChannelData(this.$route.params.id || "");
    }

    mounted(): void {
        EventBus.$on("updateFeeds", async () =>
            await this.setChannelData(this.$route.params.id || ""));
        EventBus.$on("markAllAsRead", async () =>
            await this.markRead(this.data.map(feedItem => feedItem.id)));
    }

    @Watch('$route')
    private async onRouteUpdate(to: Route, from: Route, next: Function): Promise<void> {
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

    private async markRead(ids: Array<string>): Promise<void> {
        try {
            await NetworkService.markRead(ids);
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        this.data.forEach(item => {
            if (ids.indexOf(<string>item.id) !== -1) {
                item.read = true;
            }
        });
    }
}