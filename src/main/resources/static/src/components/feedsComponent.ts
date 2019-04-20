import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {Route} from "vue-router";
import EventBus from "../eventBus";
import {FeedItem, NetworkService} from "../services/networkService";
import SettingsService from "../services/settingsService";

@Component({
    template: `
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
    `
})
export default class FeedsComponent extends Vue {

    private data: Array<FeedItem> = [];

    private settingsService: SettingsService;

    async beforeMount(): Promise<void> {
        this.settingsService = new SettingsService();
        await this.getChannels(Number(this.$route.params.id));
    }

    mounted(): void {
        EventBus.$on("updateFeeds", async () => await this.getChannels(null));
    }

    @Watch('$route')
    private async onRouteUpdate(to: Route, from: Route, next: Function): Promise<void> {
        await this.getChannels(Number(to.params.id))
    }

    private async getChannels(id: number | null) {
        try {
            const feedItems = id ?
                await NetworkService.getFeedsByChannelId(Number(id)) : await NetworkService.getAllFeeds();
            this.data = (await this.settingsService.getSettings()).hideRead ?
                feedItems.filter(feedItem => !feedItem.read) : feedItems;
            if (!this.data.length) {
                EventBus.$emit("info", "No feeds. Refresh later");
            }
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