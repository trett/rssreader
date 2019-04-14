import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {Channel, NetworkService} from "../services/networkService";

@Component({
    template: `
<v-app>
    <v-navigation-drawer dark app>
        <v-list dense class="pt-0">
            <v-list-tile to="/settings" exact="true" exact-active-class="/settings">
                <v-list-tile-action><i class="fa fa-database" aria-hidden="true"></i></v-list-tile-action>
                <v-list-tile-content>Settings</v-list-tile-content>
            </v-list-tile>
            <v-list-tile to="/" exact="true" exact-active-class="/">
                <v-list-tile-action><i class="fa fa-rss" aria-hidden="true"></i></v-list-tile-action>
                <v-list-tile-content>All channels</v-list-tile-content>
            </v-list-tile>
            <v-list-tile v-for="channel in channels" :key="channel.title" :to="'/channel/' + channel.id"
                            exact="true" :exact-active-class="'/channel' + channel.id"">
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
            <v-alert :value="!!$getError()" dismissible color="error" icon="warning" outline>
                {{ $getError() }} 
            </v-alert>
            <router-view></router-view>
        </v-container>
    </v-content>
</v-app>
`
})
export default class Main extends Vue {

    private channels: Array<Channel> = [];

    private newChannel = "";

    private dialog = false;

    async beforeMount(): Promise<void> {
        try {
            this.channels = await NetworkService.getChannels();
        } catch (e) {
            Vue.prototype.$setError(e.message);
        }
    };

    private async refresh(): Promise<void> {
        try {
            return NetworkService.updateChannels();
        } catch (e) {
            Vue.prototype.$setError(e.message);
        }
    }

    private async addChannel(): Promise<void> {
        try {
            const channelId = await NetworkService.addChannel(this.newChannel);
            this.channels = await NetworkService.getChannels();
            this.$router.push({path: `/channel/${channelId}`});
        } catch (e) {
            Vue.prototype.$setError(e.message);
        }
    }

    @Watch("dialog")
    private onDialogVisibleChange(val: any, oldVal: any): void {
        if (val) {
            this.newChannel = "";
        }
    }
}
