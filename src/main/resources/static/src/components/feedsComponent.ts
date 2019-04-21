import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {Route} from "vue-router";
import EventBus from "../eventBus";
import {FeedItem, NetworkService} from "../services/networkService";
import SettingsService from "../services/settingsService";

@Component({
    template: `
        <p v-if="!data.length" class="text-md-center" style="color: #666666">No feeds. Refresh later</p>
        <template v-else>
            <ul class="feed-list">
                <li v-for="feedItem in data" class="my-3">
                    <a  :href="feedItem.link"
                        @click="markRead(feedItem.id)"
                        target="_blank"
                        v-bind:class="{read: feedItem.read, new: !feedItem.read}">
                        {{ feedItem.title }}
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
        return this.setChannel(this.$route.params.id || "");
    }

    mounted(): void {
        EventBus.$on("updateFeeds", async () => await this.setChannel(this.$route.params.id || ""));
    }

    @Watch('$route')
    private async onRouteUpdate(to: Route, from: Route, next: Function): Promise<void> {
        return this.setChannel(to.params.id || "");
    }

    private async setChannel(id: string) {
        try {
            const feedItems = id ?
                await NetworkService.getFeedsByChannelId(Number(id)) : await NetworkService.getAllFeeds();
            this.data = (await this.settingsService.getSettings()).hideRead ?
                feedItems.filter(feedItem => !feedItem.read) : feedItems;
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async markRead(id: number): Promise<void> {
        try {
            await NetworkService.markRead(id);
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        this.data.forEach(item => {
            if (item.id === id) {
                item.read = true;
            }
        });
    }
}