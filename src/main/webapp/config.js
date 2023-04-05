let vpceSelect = document.querySelector("[name='ifVpce']");
const vpcIdTextbox = document.querySelector(".vpcId");
vpcIdTextbox.disabled = true;
const subnetIdTextbox = document.querySelector(".subnetId");
subnetIdTextbox.disabled = true;
const securityGroupIdTextbox = document.querySelector(".securityGroupId");
securityGroupIdTextbox.disabled = true;
const projectSelect = document.querySelector(".projectSelect");

// disable vpce select option if project already has VPC Settings
if (vpcIdTextbox && vpcIdTextbox.value) {
    vpceSelect.disabled = true;
}

// check again for VPC status every time a new project is selected
const handleChange = () => {
    updateVpc(projectSelect.options[projectSelect.selectedIndex].text);
}

// updates textboxes related to VPC info when new project is selected
function updateVpc(projectName) {
    updateVpcIdTextbox(projectName);
    updateSubnetIdsTextbox(projectName);
    updateSecurityGroupIdsTextbox(projectName);
}

// updates vpcId textbox
function updateVpcIdTextbox (projectName) {
    backend.fetchVpcIdFromProjectName(projectName, function(t) {
        response = t.responseObject();
        vpcIdTextbox.value = response;

        // see if vpce option needs to be disable
        if (vpcIdTextbox && vpcIdTextbox.value) {
            vpceSelect.disabled = true;
        } else {
            vpceSelect.disabled = false;
        }
    })
}

// updates subnetId textbox
function updateSubnetIdsTextbox (projectName) {
    backend.fetchSubnetIdsFromProjectName(projectName, function(t) {
        response = t.responseObject();
        subnetIdTextbox.value = response;
    })
}

// updates securityGroupIds textbox
function updateSecurityGroupIdsTextbox (projectName) {
    backend.fetchSecurityGroupIdsFromProjectName(projectName, function(t) {
        response = t.responseObject();
        securityGroupIdTextbox.value = response;
    })
}

projectSelect.addEventListener("change", handleChange);


