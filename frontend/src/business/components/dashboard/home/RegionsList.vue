<template>
  <el-card class="table-card" v-loading="result.loading">
    <template v-slot:header>
      <el-row>
        <el-col :span="16">
          <span class="title">{{$t('account.regions')}}</span>
        </el-col>
        <el-col :span="3">
          <span class="title-account">{{data}}</span>
        </el-col>
        <el-col :span="1">
          <el-divider direction="vertical"></el-divider>
        </el-col>
        <el-col :span="4" style="max-height: 20px;">
          <el-dropdown @command="handleCommand">
            <span class="el-dropdown-link">
              {{$t('account.cloud_account_list')}}<i class="el-icon-arrow-down el-icon--right"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item
                v-for="item in accounts"
                :key="item.id"
                :label="item.name"
                :value="item.id"
                :command="item">
                {{item.name}}
              </el-dropdown-item>
              <el-dropdown-item divided :command="{id: 'all', name: $t('rule.all')}">{{$t('rule.all')}}</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </el-col>
      </el-row>
    </template>
    <regions-pie-chart ref="regions"/>
  </el-card>
</template>

<script>
  import RegionsPieChart from "@/business/components/common/chart/RegionsPieChart";

  export default {
    name: "RegionsList",
    components: {
      RegionsPieChart
    },
    data() {
      return {
        result: {},
        accounts: [],
        data: this.$t('rule.all'),
        accountId: null,
      }
    },

    methods: {
      list() {
        let url = "/account/allList";
        this.result = this.$get(url, response => {
          if (response.data != undefined && response.data != null) {
            this.accounts = response.data;
          }
        });
      },
      handleCommand(command) {
        this.accountId = command.id;
        this.data = command.name;
        this.$refs.regions.focus(this.accountId);
      },
    },

    created() {
      this.list();
    },
    activated() {
    }
  }
</script>

<style scoped>
.table-card {
  margin-bottom: 2%;
  min-height: 46%;
}
.el-dropdown-link {
  cursor: pointer;
  color: #409EFF;
}
.el-icon-arrow-down {
  font-size: 12px;
}
.title-account{
  color: #e43235;
}
</style>
